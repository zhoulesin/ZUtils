package com.zhoulesin.zutils.llm

import com.zhoulesin.zutils.engine.core.FunctionInfo
import com.zhoulesin.zutils.engine.core.MediaType
import com.zhoulesin.zutils.engine.core.OutputType
import com.zhoulesin.zutils.engine.core.Parameter
import com.zhoulesin.zutils.engine.core.ParameterType
import com.zhoulesin.zutils.engine.llm.LlmClient
import com.zhoulesin.zutils.engine.workflow.Workflow
import com.zhoulesin.zutils.engine.workflow.WorkflowResult
import com.zhoulesin.zutils.engine.workflow.WorkflowStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
data class ToolCallFunction(
    val name: String,
    val arguments: String,
)

@Serializable
data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: ToolCallFunction,
)

@Serializable
data class ResponseMessage(
    val role: String,
    val content: String? = null,
    val tool_calls: List<ToolCall>? = null,
)

@Serializable
data class Choice(
    val message: ResponseMessage,
    val finish_reason: String? = null,
)

@Serializable
data class ChatResponse(
    val choices: List<Choice>,
)

class VolcengineLlmClient(
    private val apiKey: String = "c11df110-89fe-45e8-a4a4-aac27e61522a",
    //Base URL
    //不同的工具配置的 Base URL 根据兼容的协议会有不同：
    //兼容 Anthropic 接口协议工具：https://ark.cn-beijing.volces.com/api/coding
    //兼容 OpenAI 接口协议工具：https://ark.cn-beijing.volces.com/api/coding/v3（目前还不支持 Responses API，建议使用 Chat API。）
    //请勿使用 https://ark.cn-beijing.volces.com/api/v3 ：该 Base URL 不会消耗您的 Coding Plan 额度，而是会产生额外费用。
    private val baseUrl: String = "https://ark.cn-beijing.volces.com/api/coding/v3",
    private val model: String = "Doubao-Seed-2.0-lite",
) : LlmClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    override suspend fun parseIntent(
        userInput: String,
        availableFunctions: List<FunctionInfo>,
    ): Workflow {
        val tools = availableFunctions.map { it.toToolSchema() }

        val body = buildJsonObject {
            put("model", model)
            put("messages", JsonArray(listOf(
                buildJsonObject {
                    put("role", "system")
                    put("content", buildSystemPrompt(availableFunctions))
                },
                buildJsonObject {
                    put("role", "user")
                    put("content", userInput)
                }
            )))
            put("tools", JsonArray(tools))
            put("tool_choice", "required")
            put("temperature", 0.1)
        }

        val response = post(body.toString())
        val chatResp = json.decodeFromString<ChatResponse>(response)

        val message = chatResp.choices.firstOrNull()?.message
            ?: return Workflow(emptyList(), summary = "LLM 返回为空")

        val toolCalls = message.tool_calls
        if (toolCalls.isNullOrEmpty()) {
            return Workflow(emptyList(), summary = message.content ?: "无响应")
        }

        val steps = toolCalls.mapIndexed { i, tc ->
            val args = try {
                json.decodeFromString<JsonObject>(tc.function.arguments)
            } catch (_: Exception) {
                JsonObject(emptyMap())
            }
            WorkflowStep(id = i, function = tc.function.name, args = args)
        }

        return Workflow(steps = steps, summary = userInput)
    }

    override suspend fun summarize(
        userInput: String,
        result: WorkflowResult,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("用户需求: $userInput")
        sb.appendLine("执行结果:")
        for (step in result.steps) {
            sb.appendLine("- ${step.function}: ${when (val r = step.result) {
                is com.zhoulesin.zutils.engine.core.ZResult.Success -> "成功 ${r.data}"
                is com.zhoulesin.zutils.engine.core.ZResult.Error -> "失败 ${r.message}"
            }}")
        }

        val body = buildJsonObject {
            put("model", model)
            put("messages", JsonArray(listOf(
                buildJsonObject {
                    put("role", "user")
                    put("content", sb.toString())
                }
            )))
            put("temperature", 0.3)
        }

        val response = post(body.toString())
        val chatResp = json.decodeFromString<ChatResponse>(response)
        return chatResp.choices.firstOrNull()?.message?.content ?: "总结失败"
    }

    private suspend fun post(jsonBody: String): String = withContext(Dispatchers.IO) {
        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string()
        if (!response.isSuccessful) {
            throw RuntimeException("API error ${response.code}: $body")
        }
        body ?: throw RuntimeException("Empty response")
    }

    private fun buildSystemPrompt(functions: List<FunctionInfo>): String {
        val sb = StringBuilder()
        sb.appendLine("你是 ZUtils AI 引擎的助手。你可以使用以下函数来帮助用户操作手机：")
        sb.appendLine()
        for (fn in functions) {
            sb.appendLine("- ${fn.name}: ${fn.description}")
            if (fn.parameters.isNotEmpty()) {
                for (p in fn.parameters) {
                    val req = if (p.required) " (必填)" else ""
                    sb.appendLine("  ${p.name}: ${p.type}$req - ${p.description}")
                }
            }
        }
        sb.appendLine()
        sb.appendLine("规则：")
        sb.appendLine("1. 将用户需求拆解为有序的函数调用链")
        sb.appendLine("2. 必要时一次性返回多个 tool_calls")
        sb.appendLine("3. 每个步骤的入参从用户输入中提取")
        sb.appendLine("4. 不需要参数的函数传入空对象 {}")
        sb.appendLine("5. 你必须始终通过函数调用（tool_calls）响应，禁止返回任何纯文字。")
        sb.appendLine("6. 没有任何函数能处理用户请求时，也必须调用 toast 函数并说明情况，绝不能返回文字。")
        return sb.toString()
    }
}

private fun FunctionInfo.toToolSchema(): JsonObject = buildJsonObject {
    put("type", "function")
    putJsonObject("function") {
        put("name", name)
        put("description", description)
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                parameters.forEach { param ->
                    putJsonObject(param.name) {
                        put("type", param.type.toJsonType())
                        if (param.description.isNotEmpty()) put("description", param.description)
                        param.enumValues?.let {
                            put("enum", JsonArray(it.map { JsonPrimitive(it) }))
                        }
                    }
                }
            }
            if (parameters.any { it.required }) {
                put("required", JsonArray(parameters.filter { it.required }.map { JsonPrimitive(it.name) }))
            }
        }
    }
}

private fun ParameterType.toJsonType(): String = when (this) {
    ParameterType.STRING -> "string"
    ParameterType.NUMBER -> "number"
    ParameterType.INTEGER -> "integer"
    ParameterType.BOOLEAN -> "boolean"
    ParameterType.ARRAY -> "array"
    ParameterType.OBJECT -> "object"
}

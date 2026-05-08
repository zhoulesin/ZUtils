package com.zhoulesin.zutils.llm

import com.zhoulesin.zutils.engine.core.FunctionInfo
import com.zhoulesin.zutils.engine.llm.ChatMessage
import com.zhoulesin.zutils.engine.llm.ChatResult
import com.zhoulesin.zutils.engine.llm.LlmClient
import com.zhoulesin.zutils.engine.workflow.Workflow
import com.zhoulesin.zutils.engine.workflow.WorkflowResult
import com.zhoulesin.zutils.engine.workflow.WorkflowStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
data class FunctionSchema(
    val name: String,
    val description: String,
    val parameters: List<ParamSchema> = emptyList(),
)

@Serializable
data class ParamSchema(
    val name: String,
    val description: String = "",
    val type: String = "STRING",
    val required: Boolean = false,
)

class ServerLlmClient(
    private val serverBaseUrl: String,
) : LlmClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun parseIntent(
        userInput: String,
        availableFunctions: List<FunctionInfo>,
    ): Workflow {
        val schemas = availableFunctions.map { fn ->
            FunctionSchema(
                name = fn.name,
                description = fn.description,
                parameters = fn.parameters.map { p ->
                    ParamSchema(
                        name = p.name,
                        description = p.description,
                        type = p.type.name,
                        required = p.required,
                    )
                }
            )
        }
        val schemasJson = json.encodeToString(schemas)
        val bodyJson = """{"input":${jsonEscape(userInput)},"functions":$schemasJson}"""
        return try {
            val response = post("$serverBaseUrl/api/v1/llm/parse", bodyJson)
            val root = json.parseToJsonElement(response).jsonObject
            val data = root["data"]?.jsonObject ?: return Workflow(emptyList())

            val error = data["error"]?.jsonPrimitive?.contentOrNull
            if (error != null) {
                return Workflow(emptyList(), summary = error)
            }

            val stepsJson = data["steps"]?.jsonArray ?: return Workflow(emptyList())
            val steps = stepsJson.mapIndexed { i, stepJson ->
                val obj = stepJson.jsonObject
                val name = obj["function"]?.jsonPrimitive?.contentOrNull ?: ""
                val argsObj = obj["args"]?.jsonObject ?: JsonObject(emptyMap())
                val stepType = obj["type"]?.jsonPrimitive?.contentOrNull ?: "local"
                val stepResult = obj["result"]?.jsonPrimitive?.contentOrNull
                WorkflowStep(id = i, function = name, args = argsObj,
                    type = stepType, result = stepResult)
            }
            Workflow(steps = steps, summary = userInput)

        } catch (e: Exception) {
            android.util.Log.e("ZUtils-LLM", "Server LLM call failed", e)
            Workflow(emptyList(), summary = "服务器 LLM 不可用: ${e.message}")
        }
    }

    override suspend fun chat(
        messages: List<ChatMessage>,
        availableFunctions: List<FunctionInfo>,
    ): ChatResult {
        val schemas = availableFunctions.map { fn ->
            FunctionSchema(
                name = fn.name,
                description = fn.description,
                parameters = fn.parameters.map { p ->
                    ParamSchema(name = p.name, description = p.description, type = p.type.name, required = p.required)
                }
            )
        }
        val msgsJson = json.encodeToString(messages.map { mapOf("role" to it.role, "content" to it.content) })
        val schemasJson = json.encodeToString(schemas)
        val bodyJson = """{"messages":$msgsJson,"functions":$schemasJson}"""
        return try {
            val response = post("$serverBaseUrl/api/v1/llm/chat", bodyJson)
            val root = json.parseToJsonElement(response).jsonObject
            val data = root["data"]?.jsonObject ?: return ChatResult.Error("no data")
            val toolName = data["toolName"]?.jsonPrimitive?.contentOrNull
            if (toolName != null) {
                val argsObj = data["toolArgs"]?.jsonObject ?: kotlinx.serialization.json.JsonObject(emptyMap())
                val rawDexUrl = data["dexUrl"]?.jsonPrimitive?.contentOrNull
                val dexUrl = rawDexUrl?.replace("http://localhost:8080/", "$serverBaseUrl/")
                val className = data["className"]?.jsonPrimitive?.contentOrNull
                val checksum = data["checksum"]?.jsonPrimitive?.contentOrNull
                val signature = data["signature"]?.jsonPrimitive?.contentOrNull
                val stepType = data["type"]?.jsonPrimitive?.contentOrNull ?: "local"
                ChatResult.ToolCall(toolName, argsObj, dexUrl, className, checksum, signature, stepType)
            } else {
                val text = data["text"]?.jsonPrimitive?.contentOrNull ?: ""
                ChatResult.FinalAnswer(text)
            }
        } catch (e: Exception) {
            ChatResult.Error("Chat failed: ${e.message}")
        }
    }

    override suspend fun summarize(
        userInput: String,
        result: WorkflowResult,
    ): String {
        // For now, generate a simple local summary without calling server
        val parts = result.steps.map { step ->
            when (val r = step.result) {
                is com.zhoulesin.zutils.engine.core.ZResult.Success -> "${step.function} 成功"
                is com.zhoulesin.zutils.engine.core.ZResult.Error -> "${step.function} 失败: ${r.message}"
            }
        }
        return "已执行: ${parts.joinToString("; ")}"
    }

    private suspend fun post(url: String, body: String): String = withContext(Dispatchers.IO) {
        val requestBody = body.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
        if (!response.isSuccessful) {
            throw RuntimeException("Server error ${response.code}: $responseBody")
        }
        responseBody ?: throw RuntimeException("Empty response")
    }

    private fun jsonEscape(s: String): String {
        return "\"" + s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\""
    }
}

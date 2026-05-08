package com.zhoulesin.zutils.agent

import android.util.Log
import com.zhoulesin.zutils.engine.Engine
import com.zhoulesin.zutils.engine.core.FunctionInfo
import com.zhoulesin.zutils.engine.core.FunctionSource
import com.zhoulesin.zutils.engine.core.MediaType
import com.zhoulesin.zutils.engine.core.Parameter
import com.zhoulesin.zutils.engine.core.ParameterType
import com.zhoulesin.zutils.engine.core.ZResult
import com.zhoulesin.zutils.engine.llm.ChatMessage
import com.zhoulesin.zutils.engine.llm.ChatResult
import com.zhoulesin.zutils.engine.llm.LlmClient
import com.zhoulesin.zutils.engine.workflow.Workflow
import com.zhoulesin.zutils.engine.workflow.WorkflowResult
import com.zhoulesin.zutils.engine.workflow.WorkflowStep
import com.zhoulesin.zutils.mcp.McpClient
import com.zhoulesin.zutils.mcp.McpKnownTools
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 与 UI 无关的 Agent / 工作流执行逻辑，供各产品界面调用。
 */
object AgentExecution {

    suspend fun runQuery(
        engine: Engine,
        query: String,
        llmClient: LlmClient?,
        onProgress: ((String) -> Unit)? = null,
        requestRuntimePermissions: (suspend (deniedPermissions: List<String>, forFunction: String) -> Boolean)? = null,
    ): ResultContent {
        val logs = StringBuilder()
        logs.appendLine("🤖 Agent 执行记录")
        logs.appendLine("━━━━━━━━━━━━━━━━")
        fun push(msg: String) {
            logs.appendLine(msg)
            Log.i("ZUtils-LLM", msg)
            onProgress?.invoke(logs.toString())
        }

        push("📥 输入: \"$query\"")

        if (llmClient == null) {
            return runQueryRaw(engine, parseQuery(query), requestRuntimePermissions)
        }

        val messages = mutableListOf(ChatMessage(role = "user", content = query))
        val mcpClient = McpClient()

        val mcpFunctionInfos = listOf(
            FunctionInfo(
                "email_send", "发送邮件到指定收件人",
                listOf(
                    Parameter("to", "收件人邮箱", ParameterType.STRING, required = true),
                    Parameter("subject", "邮件主题", ParameterType.STRING, required = true),
                    Parameter("body", "邮件正文", ParameterType.STRING, required = true),
                ),
                source = FunctionSource.REMOTE,
            ),
            FunctionInfo(
                "document_summarize", "对文档内容进行摘要总结",
                listOf(Parameter("content", "文档文本内容", ParameterType.STRING, required = true)),
                source = FunctionSource.REMOTE,
            ),
        )

        var maxTurns = 10
        var turn = 0
        while (maxTurns-- > 0) {
            turn++
            push("")
            push("── 第 $turn 轮 ──")
            push("💭 思考中...")
            val allFuncs = engine.getAllAvailableInfos() + mcpFunctionInfos
            val result = llmClient.chat(messages, allFuncs)

            when (result) {
                is ChatResult.ToolCall -> {
                    val fn = result.function
                    val argsStr = result.args.toString()
                    push("🔧 调用: $fn")
                    if (argsStr.length < 100) push("   参数: $argsStr")

                    val output = if (fn in McpKnownTools.ALL) {
                        mcpClient.callTool(fn, result.args)
                    } else {
                        val wfStep = WorkflowStep(
                            id = 0,
                            function = fn,
                            args = result.args,
                            type = result.type,
                            dexUrl = result.dexUrl,
                            className = result.className,
                            checksum = result.checksum,
                            signature = result.signature,
                        )
                        val workflow = Workflow(steps = listOf(wfStep))
                        val wfResult = engine.execute(workflow, requestRuntimePermissions)
                        val stepResult = wfResult.steps.firstOrNull()
                        when (val r = stepResult?.result) {
                            is ZResult.Success -> r.toDisplayText
                            is ZResult.Error -> "执行失败: ${r.message}"
                            else -> "无结果"
                        }
                    }
                    push("✅ 结果: ${output.take(150)}${if (output.length > 150) "…" else ""}")
                    messages.add(
                        ChatMessage(
                            role = "user",
                            content = "$fn 的返回结果：$output\n\n根据结果决定下一步，如果任务完成请总结回复用户。",
                        ),
                    )
                }
                is ChatResult.FinalAnswer -> {
                    push("💬 ${result.text}")
                    logs.appendLine("━━━━━━━━━━━━━━━━")
                    logs.append("✅ 最终回答:\n${result.text}")
                    return ResultContent.Text(logs.toString())
                }
                is ChatResult.Error -> {
                    push("⚠️ Agent 错误: ${result.message}")
                    return runQueryRaw(engine, parseQuery(query), requestRuntimePermissions)
                }
            }
        }
        push("⏰ 执行超时")
        return ResultContent.Text(logs.toString())
    }

    private suspend fun runQueryRaw(
        engine: Engine,
        workflow: Workflow,
        requestRuntimePermissions: (suspend (List<String>, String) -> Boolean)?,
    ): ResultContent {
        val result = engine.execute(workflow, requestRuntimePermissions)
        return formatResult(result)
    }

    private fun parseQuery(query: String): Workflow {
        val q = query.lowercase().trim()

        val steps = when {
            q.contains("时间") || q.contains("几点") || q.contains("time") -> {
                listOf(workflowStep("getCurrentTime", "format" to "yyyy-MM-dd HH:mm:ss"))
            }
            q.contains("设备") || q.contains("device") || q.contains("手机信息") -> {
                listOf(workflowStep("getDeviceInfo"))
            }
            q.contains("音量") || q.contains("volume") -> {
                listOf(workflowStep("系统音量功能已移除，可通过 DEX 插件获取"))
            }
            q.contains("电量") || q.contains("电池") || q.contains("battery") -> {
                listOf(workflowStep("系统电量功能已移除，可通过 DEX 插件获取"))
            }
            q.contains("亮度") || q.contains("brightness") -> {
                listOf(workflowStep("屏幕亮度功能已移除，可通过 DEX 插件获取"))
            }
            q.contains("剪贴板") || q.contains("clipboard") -> {
                if (q.contains("复制") || q.contains("写入") || q.contains("set")) {
                    val text = q.replace("复制", "").replace("写入", "").replace("剪贴板", "").replace("clipboard", "").trim()
                        .ifEmpty { "Copied by ZUtils" }
                    listOf(workflowStep("setClipboard", "text" to text))
                } else {
                    listOf(workflowStep("getClipboard"))
                }
            }
            q.contains("屏幕") || q.contains("分辨率") || q.contains("screen") -> {
                listOf(workflowStep("getScreenInfo"))
            }
            q.contains("存储") || q.contains("空间") || q.contains("storage") -> {
                listOf(workflowStep("getStorageInfo"))
            }
            q.contains("网络") || q.contains("wifi") || q.contains("network") || q.contains("流量") -> {
                listOf(workflowStep("getNetworkType"))
            }
            q.contains("连续") || q.contains("然后") -> {
                mutableListOf<WorkflowStep>().apply {
                    if (q.contains("时间") || q.contains("time")) {
                        add(workflowStep("getCurrentTime"))
                    }
                    if (q.contains("网络")) {
                        add(workflowStep("getNetworkType"))
                    }
                    if (q.contains("uuid")) {
                        add(workflowStep("uuid"))
                    }
                    if (isEmpty()) add(workflowStep("getDeviceInfo"))
                }
            }
            q.contains("通讯录") || q.contains("联系人") || q.contains("找") -> {
                val name = q.replace("通讯录", "").replace("联系人", "").replace("找", "").replace("查", "").trim()
                    .ifEmpty { " " }
                listOf(workflowStep("queryContacts", "name" to name))
            }
            q.contains("日历") || q.contains("日程") || q.contains("会议") || q.contains("提醒") -> {
                val title = q.replace("日历", "").replace("日程", "").replace("会议", "").replace("提醒", "").trim()
                    .let { if (it.length > 2) it else "新建日程" }
                listOf(workflowStep("createCalendarEvent", "title" to title))
            }
            q.contains("写") && (q.contains("文件") || q.contains("笔记") || q.contains("文档") || q.contains("保存")) -> {
                val content = q.replace("写", "").replace("文件", "").replace("笔记", "").replace("保存", "").trim()
                listOf(workflowStep("writeFile", "content" to content))
            }
            q.contains("读取") || q.contains("打开文件") || q.contains("读文件") -> {
                listOf(workflowStep("readFile", "path" to "/storage/emulated/0/Download/note.txt"))
            }
            q.contains("办公测试") || q.contains("office test") -> {
                listOf(
                    workflowStep("writeFile", "content" to "本周完成项目 A 的交付，团队协作顺畅。下周计划启动项目 B。"),
                    workflowStep("readFile", "path" to ""),
                    workflowStep("document_summarize", "content" to "本周工作总结：完成项目 A 交付"),
                    workflowStep("send_notification", "title" to "测试完成", "content" to "办公链跑通：写文件→摘要→通知"),
                )
            }
            else -> {
                listOf(workflowStep("getDeviceInfo"))
            }
        }

        return Workflow(steps = steps)
    }

    fun workflowStep(name: String, vararg args: Pair<String, String>): WorkflowStep {
        val jsonArgs = if (args.isEmpty()) JsonObject(mapOf<String, JsonElement>())
        else JsonObject(args.associate { (k, v) -> k to JsonPrimitive(v) })
        return WorkflowStep(function = name, args = jsonArgs)
    }

    private suspend fun formatResult(result: WorkflowResult): ResultContent {
        val sb = StringBuilder()
        var dataUri: String? = null
        var hasImage = false

        result.dexLoadLog?.forEach { line ->
            sb.appendLine("  $line")
        }

        for ((i, st) in result.steps.withIndex()) {
            sb.appendLine("步骤 ${i + 1}: ${st.function}")
            when (val r = st.result) {
                is ZResult.Success -> {
                    if (r.mediaType == MediaType.IMAGE_PNG) {
                        hasImage = true
                        dataUri = r.data.toString().let { json ->
                            Regex("data:image/png;base64,[a-zA-Z0-9+/=]+")
                                .find(json)?.value
                        }
                    }
                    sb.appendLine("  ✅ ${r.data}")
                }
                is ZResult.Error -> {
                    sb.appendLine("  ❌ ${r.message}")
                }
            }
            if (st.durationMs > 0) {
                sb.appendLine("  (${st.durationMs}ms)")
            }
        }
        sb.appendLine("总计: ${result.totalDurationMs}ms")
        val text = sb.toString().trimEnd()

        return if (hasImage && dataUri != null) {
            ResultContent.QrImage(dataUri = dataUri, text = text)
        } else {
            ResultContent.Text(text)
        }
    }
}

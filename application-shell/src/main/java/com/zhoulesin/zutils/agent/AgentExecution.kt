package com.zhoulesin.zutils.agent

import android.util.Log
import com.zhoulesin.zutils.engine.Engine
import com.zhoulesin.zutils.engine.llm.LlmClient

/**
 * Agent 执行逻辑。
 *
 * 输入 → Server LLM parseIntent → 完整 Workflow → Engine 线性执行 → 汇总。
 * 无客户端多轮循环，无关键词匹配。
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
        fun push(msg: String) {
            logs.appendLine(msg)
            Log.i("ZUtils-LLM", msg)
            onProgress?.invoke(logs.toString())
        }

        push("📥 输入: \"$query\"")

        if (llmClient == null) {
            return ResultContent.Text("服务器 LLM 不可用，请检查网络连接")
        }

        push("💭 正在解析意图...")
        val workflow = llmClient.parseIntent(query, engine.getLocalOnlyInfos())

        if (workflow.steps.isEmpty()) {
            val msg = workflow.summary ?: "未能解析意图"
            push("⚠️ $msg")
            return ResultContent.Text(logs.toString())
        }

        push("📋 共 ${workflow.steps.size} 步")
        for ((i, step) in workflow.steps.withIndex()) {
            push("   ${i + 1}. ${step.function}")
        }

        val workflowResult = engine.execute(
            workflow = workflow,
            requestRuntimePermissions = requestRuntimePermissions,
        )



        if (workflowResult.dexLoadLog != null) {
            for (line in workflowResult.dexLoadLog) {
                push("  $line")
            }
        }

        for ((i, sr) in workflowResult.steps.withIndex()) {
            val icon = if (sr.result is com.zhoulesin.zutils.engine.core.ZResult.Success) "✅" else "❌"
            val text = sr.result.toDisplayText.take(120)
            push("$icon 步骤 ${i + 1}: ${sr.function} → $text${if (sr.durationMs > 0) " (${sr.durationMs}ms)" else ""}")
        }

        val summary = llmClient.summarize(query, workflowResult)
        logs.appendLine("━━━━━━━━━━━━━━━━")
        logs.append("✅ $summary")
        push("✅ $summary")

        return ResultContent.Text(logs.toString())
    }
}

package com.zhoulesin.zutils.agent

/**
 * Agent 执行结果的展示模型。UI 层（ZUtils / ZOffice）各自渲染，结构共用。
 */
sealed class ResultContent {
    data class Text(val content: String) : ResultContent()
    data class QrImage(val dataUri: String, val text: String) : ResultContent()
}

enum class EntryType { TEXT, WORKFLOW }

data class HistoryEntry(
    val label: String,
    val type: EntryType,
    val params: Map<String, String> = emptyMap(),
    val result: ResultContent,
)

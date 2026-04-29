package com.zhoulesin.zutils.engine.llm

import com.zhoulesin.zutils.engine.core.FunctionInfo
import com.zhoulesin.zutils.engine.workflow.Workflow
import com.zhoulesin.zutils.engine.workflow.WorkflowResult

data class ChatMessage(
    val role: String,
    val content: String,
)

sealed class ChatResult {
    data class ToolCall(val function: String, val args: kotlinx.serialization.json.JsonObject) : ChatResult()
    data class FinalAnswer(val text: String) : ChatResult()
    data class Error(val message: String) : ChatResult()
}

interface LlmClient {
    suspend fun parseIntent(
        userInput: String,
        availableFunctions: List<FunctionInfo>,
    ): Workflow

    suspend fun chat(
        messages: List<ChatMessage>,
        availableFunctions: List<FunctionInfo>,
    ): ChatResult

    suspend fun summarize(
        userInput: String,
        result: WorkflowResult,
    ): String
}

package com.zhoulesin.zutils.engine.llm

import com.zhoulesin.zutils.engine.core.FunctionInfo
import com.zhoulesin.zutils.engine.workflow.Workflow
import com.zhoulesin.zutils.engine.workflow.WorkflowResult

interface LlmClient {
    suspend fun parseIntent(
        userInput: String,
        availableFunctions: List<FunctionInfo>,
    ): Workflow

    suspend fun summarize(
        userInput: String,
        result: WorkflowResult,
    ): String
}

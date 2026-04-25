package com.zhoulesin.zutils.engine.workflow

import com.zhoulesin.zutils.engine.core.ExecutionContext

interface WorkflowEngine {
    suspend fun execute(
        workflow: Workflow,
        context: ExecutionContext,
    ): WorkflowResult
}

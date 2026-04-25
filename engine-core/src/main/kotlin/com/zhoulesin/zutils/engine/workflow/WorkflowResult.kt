package com.zhoulesin.zutils.engine.workflow

import com.zhoulesin.zutils.engine.core.ZResult

data class StepResult(
    val stepId: Int,
    val function: String,
    val result: ZResult,
    val durationMs: Long = 0,
)

data class WorkflowResult(
    val steps: List<StepResult>,
    val totalDurationMs: Long,
    val succeeded: Boolean,
    val errorMessage: String? = null,
    val dexLoadLog: List<String>? = null,
) {
    val allSuccessful: Boolean get() = steps.all { it.result is ZResult.Success }
}

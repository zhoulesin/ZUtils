package com.zhoulesin.zutils.engine.workflow

import com.zhoulesin.zutils.engine.core.ZResult

/**
 * 单个步骤的执行结果。
 *
 * @param stepId 对应 WorkflowStep.id
 * @param function 执行的函数名
 * @param result 执行结果（成功或失败）
 * @param durationMs 执行耗时（毫秒）
 */
data class StepResult(
    val stepId: Int,
    val function: String,
    val result: ZResult,
    val durationMs: Long = 0,
)

/**
 * 整个工作流的执行结果汇总。
 *
 * @param steps 每个步骤的执行结果列表
 * @param totalDurationMs 总耗时（毫秒）
 * @param succeeded 是否全部执行成功
 * @param errorMessage 失败时的错误描述
 * @param dexLoadLog DEX 插件加载日志（仅在动态加载时非空）
 */
data class WorkflowResult(
    val steps: List<StepResult>,
    val totalDurationMs: Long,
    val succeeded: Boolean,
    val errorMessage: String? = null,
    val dexLoadLog: List<String>? = null,
) {
    val allSuccessful: Boolean get() = steps.all { it.result is ZResult.Success }
}

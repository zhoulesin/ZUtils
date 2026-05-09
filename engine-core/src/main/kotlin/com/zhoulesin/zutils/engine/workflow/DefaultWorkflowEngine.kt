package com.zhoulesin.zutils.engine.workflow

import com.zhoulesin.zutils.engine.core.ExecutionContext
import com.zhoulesin.zutils.engine.core.ZResult
import com.zhoulesin.zutils.engine.permissions.PermissionChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.system.measureTimeMillis

/**
 * WorkflowEngine 的默认实现。
 *
 * 逐个执行 Workflow.steps，查 FunctionRegistry → 权限检查 → 执行 → 记录结果。
 * MCP / Local / DEX 函数均已在 FunctionRegistry 中注册为 ZFunction，统一调度。
 *
 * 步骤间通过 pipeline 机制传递数据。任意步骤失败则中断。
 */
class DefaultWorkflowEngine : WorkflowEngine {

    override suspend fun execute(
        workflow: Workflow,
        context: ExecutionContext,
    ): WorkflowResult = withContext(Dispatchers.Default) {
        val stepResults = mutableListOf<StepResult>()
        var allSucceeded = true
        val permissionChecker = context.permissionChecker
            ?: return@withContext WorkflowResult(
                steps = emptyList(), totalDurationMs = 0, succeeded = false,
                errorMessage = "PermissionChecker not configured")
        val pipelineResults = mutableMapOf<Int, JsonElement>()

        for ((index, step) in workflow.steps.withIndex()) {
            if (context.cancelled) break

            // 查 FunctionRegistry
            val function = context.registry.get(step.function)
            if (function == null) {
                stepResults.add(
                    StepResult(
                        stepId = index,
                        function = step.function,
                        result = ZResult.fail(
                            message = "Function '${step.function}' not found in registry",
                            code = "FUNCTION_NOT_FOUND"
                        ),
                    )
                )
                allSucceeded = false
                break
            }

            // 检查 Android 权限（依据 FunctionInfo.permissions）
            val required = function.info.permissions
            val notDeclared = permissionChecker.notDeclaredInManifest(required)
            if (notDeclared.isNotEmpty()) {
                val p = notDeclared.first()
                stepResults.add(
                    StepResult(
                        stepId = index,
                        function = step.function,
                        result = ZResult.fail(
                            message = "Permission '$p' is not declared in AndroidManifest. Declare it first, then reload.",
                            code = "PERMISSION_NOT_DECLARED",
                            recoverable = false,
                        ),
                    ),
                )
                allSucceeded = false
                break
            }

            var denied = permissionChecker.declaredButNotGranted(required)
            if (denied.isNotEmpty()) {
                val gate = context.requestRuntimePermissions
                if (gate == null) {
                    val p = denied.first()
                    stepResults.add(
                        StepResult(
                            stepId = index,
                            function = step.function,
                            result = ZResult.fail(
                                message = "Permission '$p' was denied by the user.",
                                code = "MISSING_PERMISSION",
                                recoverable = true,
                            ),
                        ),
                    )
                    allSucceeded = false
                    break
                }
                val accepted = gate(denied, step.function)
                if (!accepted) {
                    stepResults.add(
                        StepResult(
                            stepId = index,
                            function = step.function,
                            result = ZResult.fail(
                                message = "已取消：未授予「${function.info.name}」所需系统权限，流程已停止。",
                                code = "PERMISSION_DENIED_BY_USER",
                                recoverable = false,
                            ),
                        ),
                    )
                    allSucceeded = false
                    break
                }
                denied = permissionChecker.declaredButNotGranted(required)
                if (denied.isNotEmpty()) {
                    stepResults.add(
                        StepResult(
                            stepId = index,
                            function = step.function,
                            result = ZResult.fail(
                                message = "仍未获得权限: ${denied.joinToString()}。请在系统设置中开启后重试。",
                                code = "MISSING_PERMISSION_AFTER_PROMPT",
                                recoverable = true,
                            ),
                        ),
                    )
                    allSucceeded = false
                    break
                }
            }

            // pipeline：将上一步的输出注入当前步骤的入参
            val mergedArgs = if (step.pipeline.isNotEmpty()) {
                val pipelineValues = PipelineResolver.resolve(step.pipeline, pipelineResults)
                JsonObject(step.args + pipelineValues)
            } else {
                step.args
            }

            // 执行函数
            var stepResult: ZResult
            val duration = measureTimeMillis {
                stepResult = try {
                    function.execute(context, mergedArgs)
                } catch (e: SecurityException) {
                    ZResult.fail("Permission denied: ${e.message}", "PERMISSION_DENIED", recoverable = true)
                } catch (e: Exception) {
                    ZResult.fail("${e::class.simpleName}: ${e.message}", "EXECUTION_ERROR")
                }
            }

            stepResults.add(
                StepResult(
                    stepId = index,
                    function = step.function,
                    result = stepResult,
                    durationMs = duration,
                )
            )

            // 成功则记入 pipelineResults，供后续步骤引用
            if (stepResult is ZResult.Success) {
                pipelineResults[index] = stepResult.data
            }

            // 失败则中断
            if (stepResult is ZResult.Error) {
                allSucceeded = false
                break
            }
        }

        val totalDuration = stepResults.sumOf { it.durationMs }
        WorkflowResult(
            steps = stepResults,
            totalDurationMs = totalDuration,
            succeeded = allSucceeded,
            errorMessage = if (allSucceeded) null
            else stepResults.lastOrNull()?.let {
                (it.result as? ZResult.Error)?.message
            },
        )
    }
}

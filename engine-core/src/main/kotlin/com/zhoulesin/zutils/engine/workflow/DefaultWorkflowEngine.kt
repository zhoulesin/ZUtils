package com.zhoulesin.zutils.engine.workflow

import com.zhoulesin.zutils.engine.core.ExecutionContext
import com.zhoulesin.zutils.engine.core.MediaType
import com.zhoulesin.zutils.engine.core.ZResult
import com.zhoulesin.zutils.permissions.PermissionCheck
import com.zhoulesin.zutils.permissions.PermissionChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.system.measureTimeMillis

/**
 * WorkflowEngine 的默认实现。
 *
 * 逐个执行 Workflow.steps，处理三种执行类型：
 * - mcp：直接使用服务器预填充的 result，跳过本地调用
 * - local/tool：查 FunctionRegistry → 权限检查 → 执行 → 记录结果
 *
 * 步骤间通过 pipeline 机制传递数据。任意步骤失败（FUNCTION_NOT_FOUND / 权限不足 / 执行异常）则中断。
 */
class DefaultWorkflowEngine : WorkflowEngine {

    override suspend fun execute(
        workflow: Workflow,
        context: ExecutionContext,
    ): WorkflowResult = withContext(Dispatchers.Default) {
        val stepResults = mutableListOf<StepResult>()
        var allSucceeded = true
        val permissionChecker = PermissionChecker(context.androidContext)
        val pipelineResults = mutableMapOf<Int, JsonElement>()

        for ((index, step) in workflow.steps.withIndex()) {
            if (context.cancelled) break

            // type=mcp：Android 统一通过 HTTP 执行，结果已填入 step.result
            if (step.type == "mcp") {
                val resultText = step.result
                if (resultText == null) {
                    stepResults.add(StepResult(stepId = index, function = step.function,
                        result = ZResult.fail("MCP 步骤未执行: ${step.function}", "MCP_NOT_EXECUTED")))
                    allSucceeded = false; break
                }
                val mediaType = if (resultText.startsWith("data:image/"))
                    MediaType.IMAGE_PNG else MediaType.TEXT
                val dataValue = if (mediaType == MediaType.IMAGE_PNG)
                    buildJsonObject { put("dataUri", JsonPrimitive(resultText)) }
                else JsonPrimitive(resultText)
                val stepResult = ZResult.Success(dataValue, mediaType)
                stepResults.add(StepResult(stepId = index, function = step.function,
                    result = stepResult, durationMs = 0))
                if (stepResult is ZResult.Success) pipelineResults[index] = stepResult.data
                continue
            }

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

            // 检查 Android 权限
            val permCheck = permissionChecker.check(function.info.permissions)
            if (permCheck !is PermissionCheck.OK) {
                val msg = when (permCheck) {
                    is PermissionCheck.NotDeclared -> "Permission '${permCheck.permission}' is not declared in AndroidManifest. Declare it first, then reload."
                    is PermissionCheck.NotGranted -> "Permission '${permCheck.permission}' was denied by the user."
                    else -> ""
                }
                stepResults.add(
                    StepResult(
                        stepId = index,
                        function = step.function,
                        result = ZResult.fail(msg, "MISSING_PERMISSION", recoverable = true),
                    )
                )
                allSucceeded = false
                break
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

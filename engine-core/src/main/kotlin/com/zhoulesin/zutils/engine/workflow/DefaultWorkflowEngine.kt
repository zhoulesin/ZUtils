package com.zhoulesin.zutils.engine.workflow

import com.zhoulesin.zutils.engine.core.ExecutionContext
import com.zhoulesin.zutils.engine.core.PermissionCheck
import com.zhoulesin.zutils.engine.core.PermissionChecker
import com.zhoulesin.zutils.engine.core.ZResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.system.measureTimeMillis

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

            if (step.type == "mcp") {
                val resultText = step.result ?: ""
                val stepResult = ZResult.ok(resultText)
                stepResults.add(
                    StepResult(
                        stepId = index,
                        function = step.function,
                        result = stepResult,
                        durationMs = 0,
                    )
                )
                if (stepResult is ZResult.Success) {
                    pipelineResults[index] = stepResult.data
                }
                continue
            }

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

            val mergedArgs = if (step.pipeline.isNotEmpty()) {
                val pipelineValues = PipelineResolver.resolve(step.pipeline, pipelineResults)
                JsonObject(step.args + pipelineValues)
            } else {
                step.args
            }

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

            if (stepResult is ZResult.Success) {
                pipelineResults[index] = stepResult.data
            }

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

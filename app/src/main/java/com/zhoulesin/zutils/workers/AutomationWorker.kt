package com.zhoulesin.zutils.workers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.zhoulesin.zutils.config.ServerConfig
import com.zhoulesin.zutils.data.DatabaseProvider
import com.zhoulesin.zutils.engine.Engine
import com.zhoulesin.zutils.engine.workflow.Workflow
import com.zhoulesin.zutils.engine.workflow.WorkflowStep
import com.zhoulesin.zutils.mcp.McpClient
import kotlinx.serialization.json.*
import java.util.concurrent.TimeUnit

class AutomationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        val ruleId = inputData.getString("rule_id") ?: return Result.failure()
        val stepsJson = inputData.getString("steps_json") ?: return Result.failure()
        val serverUrl = inputData.getString("server_url") ?: ServerConfig.DEFAULT_BASE_URL
        Log.i("ZUtils-Auto", "Automation triggered: rule=$ruleId")

        return try {
            val rawSteps = parseRawSteps(stepsJson)

            // Step 1: Execute all MCP tools via HTTP
            val mcpClient = McpClient(baseUrl = serverUrl)
            val resolvedSteps = rawSteps.map { step ->
                if (step.type == "mcp" && step.result == null) {
                    val mcpResult = mcpClient.callTool(step.function, step.args)
                    step.copy(result = mcpResult)
                } else {
                    step
                }
            }

            // Step 2: Execute workflow locally (local functions + enriched MCP results)
            val engine = Engine(androidContext = applicationContext)
            engine.execute(Workflow(resolvedSteps))

            // Step 3: Reschedule for tomorrow
            reschedule(ruleId, stepsJson)
            Log.i("ZUtils-Auto", "Automation completed: rule=$ruleId")
            Result.success()
        } catch (e: Exception) {
            Log.e("ZUtils-Auto", "Automation failed: rule=$ruleId", e)
            Result.retry()
        }
    }

    private fun parseRawSteps(stepsJson: String): List<WorkflowStep> {
        val arr = json.parseToJsonElement(stepsJson).jsonArray
        return arr.mapIndexed { i, el ->
            val obj = el.jsonObject
            val function = obj["function"]?.jsonPrimitive?.contentOrNull
                ?: obj["name"]?.jsonPrimitive?.contentOrNull ?: ""
            val argsObj = obj["args"]?.jsonObject
                ?: obj["parameters"]?.jsonObject ?: JsonObject(emptyMap())
            val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: "local"
            val result = obj["result"]?.jsonPrimitive?.contentOrNull
            WorkflowStep(id = i, function = function, args = argsObj, type = type, result = result)
        }
    }

    private suspend fun reschedule(ruleId: String, stepsJson: String) {
        val dao = DatabaseProvider.get(applicationContext).automationRuleDao()
        val rule = dao.getById(ruleId) ?: return
        if (!rule.isEnabled) return
        val (hour, minute) = parseCron(rule.cron)
        val delay = calcDelay(hour, minute)
        val serverUrl = inputData.getString("server_url") ?: ServerConfig.DEFAULT_BASE_URL
        val request = OneTimeWorkRequestBuilder<AutomationWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag("automation_${ruleId}")
            .setInputData(workDataOf(
                "rule_id" to ruleId, "steps_json" to stepsJson,
                "server_url" to serverUrl
            ))
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "automation_${ruleId}", ExistingWorkPolicy.REPLACE, request
        )
    }

    private fun parseCron(cron: String): Pair<Int, Int> {
        val parts = cron.trim().split("\\s+".toRegex())
        if (parts.size < 2) return 8 to 0
        return (parts[1].toIntOrNull() ?: 8) to (parts[0].toIntOrNull() ?: 0)
    }

    private fun calcDelay(hour: Int, minute: Int): Long {
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
        cal.set(java.util.Calendar.MINUTE, minute)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        var t = cal.timeInMillis
        if (t <= now) t += 24 * 60 * 60 * 1000L
        return t - now
    }
}

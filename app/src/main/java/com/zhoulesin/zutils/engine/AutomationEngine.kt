package com.zhoulesin.zutils.engine

import android.content.Context
import androidx.work.*
import com.zhoulesin.zutils.data.AutomationRule
import com.zhoulesin.zutils.data.AutomationRuleDao
import com.zhoulesin.zutils.mcp.McpKnownTools
import com.zhoulesin.zutils.workers.AutomationWorker
import kotlinx.serialization.json.*
import java.util.UUID
import java.util.concurrent.TimeUnit

class AutomationEngine(
    private val context: Context,
    private val dao: AutomationRuleDao,
    private val serverBaseUrl: String = com.zhoulesin.zutils.config.ServerConfig.DEFAULT_BASE_URL,
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        val MCP_TOOLS: Set<String> = McpKnownTools.ALL
    }

    /**
     * 三种类型（执行时）：
     *   mcp   → Worker 做 HTTP 调服务器 /api/v1/mcp/call
     *   tool  → Engine.resolveMissingFunctions() → DexLoader 下载 → 注册 → 执行
     *   local → FunctionRegistry 已有 → 直接执行
     *
     * 保存 enrich 时只区分 mcp / 非 mcp，tool 和 local 运行时由 Engine 自行分辨。
     */

    suspend fun loadAll(): List<AutomationRule> = dao.loadAll()

    suspend fun getById(id: String): AutomationRule? = dao.getById(id)

    suspend fun create(name: String, cron: String, rawStepsJson: String): AutomationRule {
        val enriched = enrichStepsWithType(rawStepsJson)
        val rule = AutomationRule(
            id = UUID.randomUUID().toString().take(8),
            name = name,
            cron = cron,
            stepsJson = enriched,
        )
        dao.insert(rule)
        schedule(rule)
        return rule
    }

    suspend fun toggle(id: String, enabled: Boolean) {
        dao.setEnabled(id, enabled)
        val rule = dao.getById(id) ?: return
        if (enabled) schedule(rule) else cancel(rule)
    }

    suspend fun delete(id: String) {
        val rule = dao.getById(id)
        dao.delete(id)
        if (rule != null) cancel(rule)
    }

    suspend fun rescheduleAll() {
        cancelAll()
        for (rule in dao.loadEnabled()) {
            schedule(rule)
        }
    }

    private fun enrichStepsWithType(rawStepsJson: String): String {
        return try {
            val arr = json.parseToJsonElement(rawStepsJson).jsonArray
            val enriched = arr.map { el ->
                val obj = el.jsonObject
                val function = obj["function"]?.jsonPrimitive?.contentOrNull
                    ?: obj["name"]?.jsonPrimitive?.contentOrNull ?: ""
                if (obj["type"] == null) {
                    val inferredType = if (function in MCP_TOOLS) "mcp" else "local"
                    buildJsonObject {
                        // copy all fields with key normalization
                        for ((k, v) in obj) {
                            val key = when {
                                k == "name" && obj["function"] == null -> "function"
                                k == "parameters" && obj["args"] == null -> "args"
                                else -> k
                            }
                            if (v != null) put(key, v)
                        }
                        put("type", inferredType)
                    }
                } else {
                    obj
                }
            }
            json.encodeToString(JsonArray(enriched))
        } catch (e: Exception) {
            rawStepsJson
        }
    }

    private fun schedule(rule: AutomationRule) {
        val (hour, minute) = parseCron(rule.cron)
        val delay = calcDelay(hour, minute)
        val request = OneTimeWorkRequestBuilder<AutomationWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag("automation_${rule.id}")
            .setInputData(workDataOf(
                "rule_id" to rule.id,
                "steps_json" to rule.stepsJson,
                "server_url" to serverBaseUrl,
            ))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "automation_${rule.id}",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun cancel(rule: AutomationRule) {
        WorkManager.getInstance(context).cancelUniqueWork("automation_${rule.id}")
    }

    private fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag("automation_")
    }

    private fun parseCron(cron: String): Pair<Int, Int> {
        val parts = cron.trim().split("\\s+".toRegex())
        if (parts.size < 2) return (8 to 0)
        val minute = parts[0].toIntOrNull() ?: 0
        val hour = parts[1].toIntOrNull() ?: 8
        return hour to minute
    }

    private fun calcDelay(hour: Int, minute: Int): Long {
        val now = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, hour)
        calendar.set(java.util.Calendar.MINUTE, minute)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        var target = calendar.timeInMillis
        if (target <= now) {
            target += 24 * 60 * 60 * 1000L
        }
        return target - now
    }
}

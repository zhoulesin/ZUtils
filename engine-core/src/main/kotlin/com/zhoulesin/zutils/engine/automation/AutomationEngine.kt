package com.zhoulesin.zutils.engine.automation

interface AutomationEngine {
    data class CreatedRule(val id: String, val name: String, val cron: String)

    suspend fun create(name: String, cron: String, stepsJson: String): CreatedRule
}

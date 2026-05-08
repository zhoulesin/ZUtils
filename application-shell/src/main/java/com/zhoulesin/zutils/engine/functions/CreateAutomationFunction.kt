package com.zhoulesin.zutils.engine.functions

import com.zhoulesin.zutils.engine.AutomationEngine
import com.zhoulesin.zutils.engine.core.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

class CreateAutomationFunction(
    private val automationEngine: AutomationEngine,
) : ZFunction {
    override val info = FunctionInfo(
        name = "create_automation",
        description = "创建自动化定时规则。每天固定时间执行一组步骤",
        parameters = listOf(
            Parameter(name = "name", description = "规则名称", type = ParameterType.STRING, required = true),
            Parameter(name = "cron", description = "Cron 表达式，如'0 8 * * *'表示每天8点。格式: 分 时 * * *", type = ParameterType.STRING, required = true),
            Parameter(name = "steps", description = "执行的步骤列表 JSON 字符串", type = ParameterType.STRING, required = true),
        ),
        outputType = OutputType.NONE,
    )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult {
        val name = (args["name"] as? JsonPrimitive)?.content ?: return ZResult.fail("缺少 name")
        val cron = (args["cron"] as? JsonPrimitive)?.content ?: return ZResult.fail("缺少 cron")
        val steps = (args["steps"] as? JsonPrimitive)?.content ?: return ZResult.fail("缺少 steps")
        val rule = automationEngine.create(name, cron, steps)
        return ZResult.ok("已创建自动化规则 '${rule.name}'，ID: ${rule.id}，触发时间: ${rule.cron}")
    }
}

package com.zhoulesin.zutils.functions

import com.zhoulesin.zutils.automation.UiAutomationEngine
import com.zhoulesin.zutils.automation.UiStep
import com.zhoulesin.zutils.engine.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

class SendWeChatMessageFunction : ZFunction {
    override val info = FunctionInfo(
        name = "sendWeChatMessage",
        description = "通过 AccessibilityService 自动发送微信消息给指定联系人",
        parameters = listOf(
            Parameter("contactName", "微信联系人昵称", ParameterType.STRING, required = true),
            Parameter("message", "消息内容", ParameterType.STRING, required = true),
        ),
        outputType = OutputType.TEXT,
    )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult = withContext(Dispatchers.IO) {
        try {
            val contactName = args["contactName"]?.toString()?.removeSurrounding("\"")
                ?: return@withContext ZResult.fail("缺少联系人名称")
            val message = args["message"]?.toString()?.removeSurrounding("\"")
                ?: return@withContext ZResult.fail("缺少消息内容")

            val result = UiAutomationEngine().sendChatMessage(contactName, message)
            return@withContext when (result) {
                is com.zhoulesin.zutils.automation.UiResult.Success -> {
                    ZResult.ok("已发送微信消息给「$contactName」")
                }
                is com.zhoulesin.zutils.automation.UiResult.Failure -> {
                    ZResult.fail("发送失败: ${result.reason}", "AUTOMATION_ERROR")
                }
            }
        } catch (e: SecurityException) {
            ZResult.fail("无障碍服务未开启", "PERMISSION_DENIED", recoverable = true)
        } catch (e: Exception) {
            ZResult.fail("发送微信消息失败: ${e.message}", "WECHAT_ERROR")
        }
    }
}

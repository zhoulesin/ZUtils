package com.zhoulesin.zutils.engine.functions

import com.zhoulesin.zutils.engine.core.*
import com.zhoulesin.zutils.notification.NotificationHelper
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

class SendNotificationFunction : ZFunction {
    override val info = FunctionInfo(
        name = "send_notification",
        description = "发送系统通知到通知栏",
        parameters = listOf(
            Parameter(name = "title", description = "通知标题", type = ParameterType.STRING, required = true),
            Parameter(name = "content", description = "通知内容", type = ParameterType.STRING, required = true),
        ),
        outputType = OutputType.NONE,
    )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult {
        val title = (args["title"] as? JsonPrimitive)?.content ?: "ZUtils"
        val content = (args["content"] as? JsonPrimitive)?.content ?: ""
        NotificationHelper.send(context.androidContext, title, content)
        return ZResult.ok("通知已发送: $title - $content")
    }
}

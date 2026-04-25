package com.zhoulesin.zutils.engine.functions

import android.widget.Toast
import com.zhoulesin.zutils.engine.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

class ToastFunction : ZFunction {
    override val info = FunctionInfo(
        name = "toast",
        description = "Show a short toast message on the screen",
        parameters = listOf(
            Parameter(
                name = "message",
                description = "The message text to display",
                type = ParameterType.STRING,
                required = false,
            )
        ),
        outputType = OutputType.NONE,
    )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult {
        val message = args["message"]
        val text = if (message == null) ""
        else when {
            message is kotlinx.serialization.json.JsonPrimitive -> message.content
            message is kotlinx.serialization.json.JsonObject -> message.toString()
            else -> message.toString()
        }

        withContext(Dispatchers.Main) {
            Toast.makeText(context.androidContext, text, Toast.LENGTH_SHORT).show()
        }
        return ZResult.ok("Toast shown: $text")
    }
}

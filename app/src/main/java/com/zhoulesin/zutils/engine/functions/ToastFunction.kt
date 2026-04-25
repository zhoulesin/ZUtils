package com.zhoulesin.zutils.engine.functions

import android.widget.Toast
import com.zhoulesin.zutils.engine.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

class ToastFunction : ZFunction {
    override val info = FunctionInfo(
        name = "toast",
        description = "Show a short toast message on the screen",
        parameters = listOf(
            Parameter(
                name = "message",
                description = "The message text to display",
                type = ParameterType.STRING,
                required = true,
            )
        ),
        outputType = OutputType.NONE,
    )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult {
        val message = args["message"]?.jsonPrimitive?.content
            ?: return ZResult.fail("Missing required argument: message", "MISSING_ARG")

        withContext(Dispatchers.Main) {
            Toast.makeText(context.androidContext, message, Toast.LENGTH_SHORT).show()
        }
        return ZResult.ok("Toast shown: $message")
    }
}

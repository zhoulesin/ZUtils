package com.zhoulesin.zutils.engine.functions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.zhoulesin.zutils.engine.core.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class SetClipboardFunction : ZFunction {
    override val info = FunctionInfo(
        name = "setClipboard",
        description = "Copy text to the system clipboard",
        parameters = listOf(
            Parameter(
                name = "text",
                description = "The text to copy to clipboard",
                type = ParameterType.STRING,
                required = true,
            ),
        ),
        outputType = OutputType.NONE,
    )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult {
        val text = args["text"]?.jsonPrimitive?.content
            ?: return ZResult.fail("Missing required argument: text", "MISSING_ARG")

        val clipboard = context.androidContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return ZResult.fail("Clipboard service not available", "SERVICE_UNAVAILABLE")

        clipboard.setPrimaryClip(ClipData.newPlainText("ZUtils", text))
        return ZResult.ok("Copied to clipboard: $text")
    }
}

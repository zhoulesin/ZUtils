package com.zhoulesin.zutils.engine.functions

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.zhoulesin.zutils.engine.core.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class GetClipboardFunction : ZFunction {
    override val info = FunctionInfo(
        name = "getClipboard",
        description = "Read the current text from the system clipboard",
    )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult {
        val clipboard = context.androidContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return ZResult.fail("Clipboard service not available", "SERVICE_UNAVAILABLE")

        if (!clipboard.hasPrimaryClip()) {
            return ZResult.fail("Clipboard is empty", "CLIPBOARD_EMPTY")
        }

        val clip = clipboard.primaryClip?.getItemAt(0)
        val text = clip?.text?.toString()
            ?: clip?.coerceToText(context.androidContext)?.toString()
            ?: return ZResult.fail("Could not read clipboard content", "READ_FAILED")

        return ZResult.ok(text)
    }
}

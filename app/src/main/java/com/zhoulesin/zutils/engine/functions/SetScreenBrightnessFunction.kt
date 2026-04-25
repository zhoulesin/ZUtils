package com.zhoulesin.zutils.engine.functions

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.zhoulesin.zutils.engine.core.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class SetScreenBrightnessFunction : ZFunction {
    override val info = FunctionInfo(
        name = "setScreenBrightness",
        description = "Set the screen brightness level (0-100)",
        parameters = listOf(
            Parameter(
                name = "level",
                description = "Brightness level from 0 to 100",
                type = ParameterType.NUMBER,
                required = true,
            )
        ),
        permissions = listOf(android.Manifest.permission.WRITE_SETTINGS),
        outputType = OutputType.NONE,
    )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult {
        val level = args["level"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: return ZResult.fail("Missing or invalid argument: level", "MISSING_ARG")

        if (level !in 0..100) {
            return ZResult.fail("Level must be between 0 and 100", "INVALID_ARG")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.System.canWrite(context.androidContext)
        ) {
            return ZResult.fail(
                "Write settings permission not granted",
                "PERMISSION_DENIED",
                recoverable = true,
            )
        }
        val brightness = (level * 255) / 100
        ContentValues().apply {
            put(Settings.System.SCREEN_BRIGHTNESS, brightness)
            context.androidContext.contentResolver.update(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
                this, null, null
            )
        }

        return ZResult.ok("Screen brightness set to $level%")
    }
}

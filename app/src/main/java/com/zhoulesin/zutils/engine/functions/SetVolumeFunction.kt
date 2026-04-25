package com.zhoulesin.zutils.engine.functions

import android.content.Context
import android.media.AudioManager
import com.zhoulesin.zutils.engine.core.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class SetVolumeFunction : ZFunction {
    override val info = FunctionInfo(
        name = "setVolume",
        description = "Set the media volume level (0-100, percentage)",
        parameters = listOf(
            Parameter(
                name = "level",
                description = "Volume level from 0 to 100",
                type = ParameterType.NUMBER,
                required = true,
            ),
        ),
        outputType = OutputType.NONE,
    )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult {
        val level = args["level"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: return ZResult.fail("Missing or invalid argument: level", "MISSING_ARG")

        if (level !in 0..100) {
            return ZResult.fail("Level must be between 0 and 100", "INVALID_ARG")
        }

        val audioManager = context.androidContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ZResult.fail("Audio service not available", "SERVICE_UNAVAILABLE")

        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (level * max) / 100
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        return ZResult.ok("Media volume set to $level%")
    }
}

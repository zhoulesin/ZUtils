package com.zhoulesin.zutils.engine.functions

import android.content.Context
import android.media.AudioManager
import com.zhoulesin.zutils.engine.core.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class GetVolumeFunction : ZFunction {
    override val info = FunctionInfo(
        name = "getVolume",
        description = "Get the current media volume level (0 - max)",
        outputType = OutputType.OBJECT,
    )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult {
        val audioManager = context.androidContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ZResult.fail("Audio service not available", "SERVICE_UNAVAILABLE")

        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val result = buildMap {
            put("current", JsonPrimitive(current))
            put("max", JsonPrimitive(max))
            put("percentage", JsonPrimitive(if (max > 0) (current * 100) / max else 0))
        }
        return ZResult.Success(kotlinx.serialization.json.JsonObject(result))
    }
}

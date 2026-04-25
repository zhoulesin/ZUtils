package com.zhoulesin.zutils.engine.functions

import com.zhoulesin.zutils.engine.core.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.*

class GetCurrentTimeFunction : ZFunction {
    override val info = FunctionInfo(
        name = "getCurrentTime",
        description = "Get the current system date and time",
    )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult {
        val format = args["format"]?.jsonPrimitive?.content ?: "yyyy-MM-dd HH:mm:ss"
        return try {
            val sdf = SimpleDateFormat(format, Locale.getDefault())
            ZResult.ok(sdf.format(Date()))
        } catch (e: IllegalArgumentException) {
            ZResult.fail("Invalid date format pattern: ${e.message}", "INVALID_FORMAT")
        }
    }
}

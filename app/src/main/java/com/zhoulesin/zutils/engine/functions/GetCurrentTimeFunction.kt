package com.zhoulesin.zutils.engine.functions

import com.zhoulesin.zutils.engine.core.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.*

class GetCurrentTimeFunction : ZFunction {
    override val info = FunctionInfo(
        name = "getCurrentTime",
        description = "Get the current system date and time",
        parameters = listOf(
            Parameter(
                name = "format",
                description = "Date/time format pattern (e.g. \"yyyy-MM-dd HH:mm:ss\"). Defaults to \"yyyy-MM-dd HH:mm:ss\"",
                type = ParameterType.STRING,
                required = false,
                defaultValue = JsonPrimitive("yyyy-MM-dd HH:mm:ss"),
            )
        ),
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

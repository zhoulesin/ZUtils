package com.zhoulesin.zutils.engine.functions

import android.util.Base64
import com.zhoulesin.zutils.engine.core.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.nio.charset.StandardCharsets

class Base64Function : ZFunction {
    override val info = FunctionInfo(
        name = "base64",
        description = "Encode or decode a string using Base64",
        parameters = listOf(
            Parameter(
                name = "action",
                description = "Either \"encode\" or \"decode\"",
                type = ParameterType.STRING,
                required = true,
                enumValues = listOf("encode", "decode"),
            ),
            Parameter(
                name = "text",
                description = "The text to encode or decode",
                type = ParameterType.STRING,
                required = true,
            ),
        ),
        outputType = OutputType.TEXT,
    )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult {
        val action = args["action"]?.jsonPrimitive?.content?.lowercase()
            ?: return ZResult.fail("Missing required argument: action", "MISSING_ARG")
        val text = args["text"]?.jsonPrimitive?.content
            ?: return ZResult.fail("Missing required argument: text", "MISSING_ARG")

        return try {
            when (action) {
                "encode" -> {
                    val encoded = Base64.encodeToString(
                        text.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP
                    )
                    ZResult.ok(encoded)
                }
                "decode" -> {
                    val decoded = String(
                        Base64.decode(text, Base64.NO_WRAP), StandardCharsets.UTF_8
                    )
                    ZResult.ok(decoded)
                }
                else -> ZResult.fail("Invalid action: $action (use encode/decode)", "INVALID_ARG")
            }
        } catch (e: Exception) {
            ZResult.fail("Base64 $action failed: ${e.message}", "EXEC_ERROR")
        }
    }
}

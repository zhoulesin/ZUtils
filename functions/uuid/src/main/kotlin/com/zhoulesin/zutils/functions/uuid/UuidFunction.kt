package com.zhoulesin.zutils.functions.uuid

import com.zhoulesin.zutils.engine.core.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

class UuidFunction : ZFunction {
    override val info = FunctionInfo(
        name = "uuid",
        description = "Generate a random UUID v4 string",
        outputType = OutputType.TEXT,
    )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult {
        return ZResult.ok(UUID.randomUUID().toString())
    }
}

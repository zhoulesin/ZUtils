package com.zhoulesin.zutils.engine.core

import kotlinx.serialization.json.JsonObject

interface ZFunction {
    val info: FunctionInfo

    val requiredDependencies: Map<String, String> get() = emptyMap()

    suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult
}

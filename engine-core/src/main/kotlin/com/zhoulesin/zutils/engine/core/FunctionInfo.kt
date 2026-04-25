package com.zhoulesin.zutils.engine.core

import kotlinx.serialization.Serializable

enum class OutputType {
    TEXT,
    NUMBER,
    BOOLEAN,
    OBJECT,
    NONE,
}

@Serializable
data class FunctionInfo(
    val name: String,
    val description: String,
    val parameters: List<Parameter> = emptyList(),
    val permissions: List<String> = emptyList(),
    val source: FunctionSource = FunctionSource.BUILT_IN,
    val outputType: OutputType = OutputType.TEXT,
)

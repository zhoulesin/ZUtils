package com.zhoulesin.zutils.engine.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Parameter(
    val name: String,
    val description: String,
    val type: ParameterType,
    val required: Boolean = false,
    val defaultValue: JsonElement? = null,
    val enumValues: List<String>? = null,
)

@Serializable
enum class ParameterType {
    STRING,
    NUMBER,
    BOOLEAN,
    INTEGER,
    ARRAY,
    OBJECT,
}

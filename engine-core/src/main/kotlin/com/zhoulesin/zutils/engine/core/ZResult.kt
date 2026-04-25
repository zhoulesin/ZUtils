package com.zhoulesin.zutils.engine.core

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

enum class MediaType {
    TEXT,
    IMAGE_PNG,
    MARKDOWN,
    JSON,
}

sealed interface ZResult {
    val toDisplayText: String

    data class Success(
        val data: JsonElement,
        val mediaType: MediaType = MediaType.TEXT,
    ) : ZResult {
        override val toDisplayText: String get() = data.toString()
    }

    data class Error(
        val message: String,
        val code: String? = null,
        val recoverable: Boolean = false,
    ) : ZResult {
        override val toDisplayText: String get() = "[$code] $message"
    }

    companion object {
        fun ok(text: String) = Success(JsonPrimitive(text))
        fun ok(number: Number) = Success(JsonPrimitive(number.toDouble()))
        fun ok(boolean: Boolean) = Success(JsonPrimitive(boolean))
        fun fail(message: String, code: String? = null, recoverable: Boolean = false) = Error(message, code, recoverable)
    }
}

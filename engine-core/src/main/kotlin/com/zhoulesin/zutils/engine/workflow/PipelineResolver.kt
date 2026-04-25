package com.zhoulesin.zutils.engine.workflow

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object PipelineResolver {

    fun resolve(
        pipeline: Map<String, String>,
        stepResults: Map<Int, JsonElement>,
    ): JsonObject {
        val resolved = mutableMapOf<String, JsonElement>()
        for ((paramName, expr) in pipeline) {
            val value = resolveExpression(expr, stepResults)
            if (value != null) {
                resolved[paramName] = toPrimitive(value)
            }
        }
        return JsonObject(resolved)
    }

    private fun toPrimitive(element: JsonElement): JsonPrimitive {
        return when (element) {
            is JsonPrimitive -> element
            else -> JsonPrimitive(element.toString())
        }
    }

    private fun resolveExpression(
        expr: String,
        results: Map<Int, JsonElement>,
    ): JsonElement? {
        if (!expr.startsWith("{") || !expr.endsWith("}")) return null
        val path = expr.removeSurrounding("{", "}").trim()
        if (path.isEmpty()) return null

        val dotIndex = path.indexOf('.')
        val stepId = if (dotIndex >= 0) path.substring(0, dotIndex) else path
        val fieldPath = if (dotIndex >= 0) path.substring(dotIndex + 1) else ""

        val id = stepId.toIntOrNull() ?: return null
        val data = results[id] ?: return null

        if (fieldPath.isEmpty()) return data
        return navigateJson(data, fieldPath)
    }

    private fun navigateJson(element: JsonElement, path: String): JsonElement? {
        val parts = path.split(".").dropWhile { it.isEmpty() }
        var current = element
        for (part in parts) {
            current = when (current) {
                is JsonObject -> current[part] ?: return null
                else -> return null
            }
        }
        return current
    }
}

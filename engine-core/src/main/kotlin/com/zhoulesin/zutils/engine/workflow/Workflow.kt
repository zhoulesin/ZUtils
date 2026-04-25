package com.zhoulesin.zutils.engine.workflow

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class WorkflowStep(
    val id: Int = 0,
    val function: String,
    val args: JsonObject = JsonObject(emptyMap()),
    val description: String? = null,
)

@Serializable
data class Workflow(
    val steps: List<WorkflowStep>,
    val summary: String? = null,
)

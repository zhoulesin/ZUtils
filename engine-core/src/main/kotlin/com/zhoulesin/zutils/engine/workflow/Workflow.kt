package com.zhoulesin.zutils.engine.workflow

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * LLM 返回的单个执行步骤，或是自动化规则中保存的一个步骤。
 *
 * @param id 步骤序号
 * @param function 要调用的函数名（对应 FunctionRegistry 中的 ZFunction.name）
 * @param args 函数参数，key-value 形式
 * @param description 可选的人类可读步骤说明
 * @param pipeline 步骤间数据传递配置，key 为当前入参名，value 为上游引用如 "{0.result}"
 * @param type 执行类型：local（本地执行）/ mcp（云端 MCP Tool）/ tool（DEX 插件）
 * @param result MCP Tool 预执行结果，type=mcp 时由服务器填充
 */
@Serializable
data class WorkflowStep(
    val id: Int = 0,
    val function: String,
    val args: JsonObject = JsonObject(emptyMap()),
    val description: String? = null,
    val pipeline: Map<String, String> = emptyMap(),
    val type: String = "local",
    val result: String? = null,
)

/**
 * LLM 返回的完整工作流，包含多个有序步骤。
 *
 * @param steps 按顺序执行的步骤列表
 * @param summary 用户输入的原始文本，或 LLM 返回的结果摘要
 */
@Serializable
data class Workflow(
    val steps: List<WorkflowStep>,
    val summary: String? = null,
)

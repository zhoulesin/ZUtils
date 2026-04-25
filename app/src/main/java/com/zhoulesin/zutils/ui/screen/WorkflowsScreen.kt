package com.zhoulesin.zutils.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhoulesin.zutils.engine.core.FunctionInfo
import com.zhoulesin.zutils.engine.workflow.Workflow
import com.zhoulesin.zutils.engine.workflow.WorkflowStep
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

enum class WorkflowType { SEQUENTIAL, PIPELINE }

data class WorkflowTemplate(
    val name: String,
    val description: String,
    val icon: String,
    val type: WorkflowType,
    val build: () -> Workflow,
)

private fun step(name: String, vararg args: Pair<String, String>): WorkflowStep {
    val jsonArgs = if (args.isEmpty()) JsonObject(emptyMap())
    else JsonObject(args.associate { (k, v) -> k to JsonPrimitive(v) })
    return WorkflowStep(function = name, args = jsonArgs)
}

private fun pipelineStep(
    name: String,
    pipeline: Map<String, String>,
    vararg args: Pair<String, String> = emptyArray(),
): WorkflowStep {
    val jsonArgs = if (args.isEmpty()) JsonObject(emptyMap())
    else JsonObject(args.associate { (k, v) -> k to JsonPrimitive(v) })
    return WorkflowStep(function = name, args = jsonArgs, pipeline = pipeline)
}

private val sequentialWorkflows = listOf(
    WorkflowTemplate("系统诊断", "设备信息 + 电量 + 网络 + 存储", "🔍", WorkflowType.SEQUENTIAL,
        build = { Workflow(listOf(
            step("getDeviceInfo"), step("getBatteryLevel"), step("getNetworkType"), step("getStorageInfo"),
        ), summary = "系统诊断") },
    ),
    WorkflowTemplate("音量与亮度", "查看音量 → 设音量70% → 提示", "🔊", WorkflowType.SEQUENTIAL,
        build = { Workflow(listOf(
            step("getVolume"), step("setVolume", "level" to "70"), step("toast", "message" to "音量已设为70%"),
        ), summary = "音量配置") },
    ),
    WorkflowTemplate("开发工具箱", "UUID → 时间 → Base64 编码", "🛠", WorkflowType.SEQUENTIAL,
        build = { Workflow(listOf(
            step("uuid"), step("getCurrentTime"), step("base64", "action" to "encode", "text" to "Hello ZUtils"),
        ), summary = "开发工具") },
    ),
    WorkflowTemplate("剪贴板操作", "写入 → 读取验证", "📋", WorkflowType.SEQUENTIAL,
        build = { Workflow(listOf(
            step("setClipboard", "text" to "ZUtils clipboard test"), step("getClipboard"),
        ), summary = "剪贴板测试") },
    ),
    WorkflowTemplate("设备全景", "时间 + 设备 + 电量 + 存储 + 网络 + 屏幕", "📊", WorkflowType.SEQUENTIAL,
        build = { Workflow(listOf(
            step("getCurrentTime"), step("getDeviceInfo"), step("getBatteryLevel"),
            step("getStorageInfo"), step("getNetworkType"), step("getScreenInfo"),
        ), summary = "设备全景") },
    ),
    WorkflowTemplate("快捷配置", "音量50% → 亮度80% → 提示", "🎛", WorkflowType.SEQUENTIAL,
        build = { Workflow(listOf(
            step("setVolume", "level" to "50"), step("setScreenBrightness", "level" to "80"),
            step("toast", "message" to "音量50% 亮度80%"),
        ), summary = "快捷配置") },
    ),
)

private val pipelineWorkflows = listOf(
    WorkflowTemplate("时间通知", "获取当前时间 → 显示为 Toast", "⏰", WorkflowType.PIPELINE,
        build = { Workflow(listOf(
            step("getCurrentTime"),
            pipelineStep("toast", pipeline = mapOf("message" to "{0}")),
        ), summary = "时间通知") },
    ),
    WorkflowTemplate("电量通知", "获取电量 → 显示为 Toast", "🔋", WorkflowType.PIPELINE,
        build = { Workflow(listOf(
            step("getBatteryLevel"),
            pipelineStep("toast", pipeline = mapOf("message" to "{0}")),
        ), summary = "电量通知") },
    ),
    WorkflowTemplate("设备名提示", "获取设备名称 → 显示为 Toast", "📱", WorkflowType.PIPELINE,
        build = { Workflow(listOf(
            step("getDeviceInfo"),
            pipelineStep("toast", pipeline = mapOf("message" to "{0.model}")),
        ), summary = "设备名提示") },
    ),
    WorkflowTemplate("时间到剪贴板", "获取时间 → 复制到剪贴板", "📋", WorkflowType.PIPELINE,
        build = { Workflow(listOf(
            step("getCurrentTime"),
            pipelineStep("setClipboard", pipeline = mapOf("text" to "{0}")),
        ), summary = "时间到剪贴板") },
    ),
)

private val sectionHeaders = listOf(
    "顺序工作流" to sequentialWorkflows,
    "管道工作流" to pipelineWorkflows,
)

@Composable
fun WorkflowsScreen(
    functions: List<FunctionInfo>,
    onExecute: (Workflow) -> Unit,
    onNewWorkflow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WorkflowsList(onExecute = onExecute, showBuilder = onNewWorkflow, modifier = modifier)
}

@Composable
private fun WorkflowsList(
    onExecute: (Workflow) -> Unit,
    showBuilder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            OutlinedButton(
                onClick = showBuilder,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("+ 创建自定义工作流")
            }
        }
        sectionHeaders.forEach { (title, templates) ->
            item {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }
            items(templates) { template ->
                WorkflowCard(template = template, onClick = { onExecute(template.build()) })
            }
        }
    }
}

@Composable
private fun WorkflowCard(template: WorkflowTemplate, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${template.icon} ${template.name}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                TypeBadge(template.type)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = template.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TypeBadge(type: WorkflowType) {
    val (text, color) = when (type) {
        WorkflowType.SEQUENTIAL -> "顺序" to MaterialTheme.colorScheme.tertiary
        WorkflowType.PIPELINE -> "管道" to MaterialTheme.colorScheme.secondary
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.2f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

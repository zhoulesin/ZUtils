package com.zhoulesin.zutils.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhoulesin.zutils.data.SavedStep
import com.zhoulesin.zutils.data.WorkflowEntity
import com.zhoulesin.zutils.data.WorkflowStorage
import com.zhoulesin.zutils.engine.core.FunctionInfo
import com.zhoulesin.zutils.engine.workflow.Workflow
import com.zhoulesin.zutils.engine.workflow.WorkflowStep
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
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

private fun pipelineStep(name: String, pipeline: Map<String, String>, vararg args: Pair<String, String> = emptyArray()): WorkflowStep {
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
            step("getCurrentTime"), pipelineStep("toast", pipeline = mapOf("message" to "{0}")),
        ), summary = "时间通知") },
    ),
    WorkflowTemplate("电量通知", "获取电量 → 显示为 Toast", "🔋", WorkflowType.PIPELINE,
        build = { Workflow(listOf(
            step("getBatteryLevel"), pipelineStep("toast", pipeline = mapOf("message" to "{0}")),
        ), summary = "电量通知") },
    ),
    WorkflowTemplate("设备名提示", "获取设备名称 → 显示为 Toast", "📱", WorkflowType.PIPELINE,
        build = { Workflow(listOf(
            step("getDeviceInfo"), pipelineStep("toast", pipeline = mapOf("message" to "{0.model}")),
        ), summary = "设备名提示") },
    ),
    WorkflowTemplate("时间到剪贴板", "获取时间 → 复制到剪贴板", "📋", WorkflowType.PIPELINE,
        build = { Workflow(listOf(
            step("getCurrentTime"), pipelineStep("setClipboard", pipeline = mapOf("text" to "{0}")),
        ), summary = "时间到剪贴板") },
    ),
)

@Composable
fun WorkflowsScreen(
    functions: List<FunctionInfo>,
    storage: WorkflowStorage,
    onExecute: (Workflow) -> Unit,
    onNewWorkflow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val savedWorkflows by storage.loadAll().collectAsState(initial = emptyList())
    var pendingExecution by remember { mutableStateOf<WorkflowEntity?>(null) }

    if (pendingExecution != null) {
        val entity = pendingExecution!!
        val steps = Json.decodeFromString<List<SavedStep>>(entity.stepsJson)
        val firstFn = functions.find { it.name == steps.firstOrNull()?.function }
        val firstArgs = steps.firstOrNull()?.args ?: JsonObject(emptyMap())
        val missingParams = firstFn?.parameters?.filter { it.required && it.name !in firstArgs }

        if (missingParams != null && missingParams.isNotEmpty()) {
            ParamInputDialog(
                params = missingParams,
                onDismiss = { pendingExecution = null },
                onConfirm = { inputs ->
                    val mergedArgs = JsonObject(firstArgs + inputs.mapValues { (_, v) -> JsonPrimitive(v) })
                    val workflowSteps = steps.mapIndexed { i, step ->
                        WorkflowStep(id = i, function = step.function, args = if (i == 0) mergedArgs else step.args, pipeline = step.pipeline)
                    }
                    onExecute(Workflow(steps = workflowSteps, summary = entity.title))
                    pendingExecution = null
                },
            )
        } else {
            val workflowSteps = steps.mapIndexed { i, step ->
                WorkflowStep(id = i, function = step.function, args = step.args, pipeline = step.pipeline)
            }
            onExecute(Workflow(steps = workflowSteps, summary = entity.title))
            pendingExecution = null
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (savedWorkflows.isNotEmpty()) {
            item { Text("我的工作流", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
            items(savedWorkflows) { entity ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { pendingExecution = entity },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entity.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            if (entity.description.isNotBlank()) {
                                Text(entity.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${entity.stepCount} 步 · ${entity.type.lowercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = {
                            scope.launch { storage.delete(entity) }
                        }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(4.dp)) }
        item {
            OutlinedButton(onClick = onNewWorkflow, modifier = Modifier.fillMaxWidth()) {
                Text("+ 创建自定义工作流")
            }
        }

        item { Text("预置工作流", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
        (sequentialWorkflows + pipelineWorkflows).forEach { template ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onExecute(template.build()) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${template.icon} ${template.name}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text(template.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ParamInputDialog(
    params: List<com.zhoulesin.zutils.engine.core.Parameter>,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, String>) -> Unit,
) {
    val inputs = remember { mutableStateMapOf<String, String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("输入参数") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                params.forEach { param ->
                    OutlinedTextField(
                        value = inputs[param.name] ?: "",
                        onValueChange = { inputs[param.name] = it },
                        label = { Text(param.name) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(inputs.toMap()) }) { Text("执行") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

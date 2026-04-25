package com.zhoulesin.zutils.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhoulesin.zutils.engine.workflow.Workflow
import com.zhoulesin.zutils.engine.workflow.WorkflowStep
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class WorkflowTemplate(
    val name: String,
    val description: String,
    val icon: String,
    val build: () -> Workflow,
)

private fun step(name: String, vararg args: Pair<String, String>): WorkflowStep {
    val jsonArgs = if (args.isEmpty()) JsonObject(emptyMap())
    else JsonObject(args.associate { (k, v) -> k to JsonPrimitive(v) })
    return WorkflowStep(function = name, args = jsonArgs)
}

val predefinedWorkflows = listOf(
    WorkflowTemplate(
        name = "系统诊断",
        description = "获取设备信息、电量、网络类型、存储空间",
        icon = "🔍",
        build = { Workflow(listOf(
            step("getDeviceInfo"),
            step("getBatteryLevel"),
            step("getNetworkType"),
            step("getStorageInfo"),
        ), summary = "系统诊断") },
    ),
    WorkflowTemplate(
        name = "音量与亮度",
        description = "查看当前音量，设置音量到 70%，提示完成",
        icon = "🔊",
        build = { Workflow(listOf(
            step("getVolume"),
            step("setVolume", "level" to "70"),
            step("toast", "message" to "音量已设为70%"),
        ), summary = "音量配置") },
    ),
    WorkflowTemplate(
        name = "开发工具箱",
        description = "生成 UUID、获取当前时间、Base64 编码",
        icon = "🛠",
        build = { Workflow(listOf(
            step("uuid"),
            step("getCurrentTime"),
            step("base64", "action" to "encode", "text" to "Hello ZUtils"),
        ), summary = "开发工具") },
    ),
    WorkflowTemplate(
        name = "剪贴板操作",
        description = "写入一条文本到剪贴板，再读出来确认",
        icon = "📋",
        build = { Workflow(listOf(
            step("setClipboard", "text" to "ZUtils clipboard test"),
            step("getClipboard"),
        ), summary = "剪贴板测试") },
    ),
    WorkflowTemplate(
        name = "设备全景",
        description = "获取时间、设备信息、电量、存储、网络、屏幕信息",
        icon = "📊",
        build = { Workflow(listOf(
            step("getCurrentTime"),
            step("getDeviceInfo"),
            step("getBatteryLevel"),
            step("getStorageInfo"),
            step("getNetworkType"),
            step("getScreenInfo"),
        ), summary = "设备全景") },
    ),
    WorkflowTemplate(
        name = "音量与亮度",
        description = "设置音量到 50%，亮度到 80%",
        icon = "🎛",
        build = { Workflow(listOf(
            step("setVolume", "level" to "50"),
            step("setScreenBrightness", "level" to "80"),
            step("toast", "message" to "音量50% 亮度80%"),
        ), summary = "快捷配置") },
    ),
)

@Composable
fun WorkflowsScreen(onExecute: (Workflow) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(predefinedWorkflows) { template ->
            WorkflowCard(template = template, onClick = { onExecute(template.build()) })
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

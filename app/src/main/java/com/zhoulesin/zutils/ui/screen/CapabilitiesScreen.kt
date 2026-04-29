package com.zhoulesin.zutils.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.zhoulesin.zutils.engine.core.FunctionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class SkillItem(
    val id: String, val name: String, val description: String, val icon: String,
    val steps: List<Pair<String, String>>, // (function, type)
    val cron: String? = null,
)

private val SKILLS = listOf(
    SkillItem("skill-weather", "查北京天气并通知我", "查询北京天气并发送通知", "🌤",
        listOf("weather_current" to "mcp", "send_notification" to "local")),
    SkillItem("skill-news", "每日新闻推送", "每天10点获取新闻并通知", "📰",
        listOf("news_headlines" to "mcp", "translate_text" to "mcp", "send_notification" to "local"),
        cron = "0 10 * * *"),
)

private val FUNCTION_CATEGORIES: Map<String, String> = mapOf(
    "send_notification" to "通知", "toast" to "通知",
    "create_automation" to "自动化",
    "getCurrentTime" to "系统", "getBatteryLevel" to "系统",
    "setScreenBrightness" to "系统", "getDeviceInfo" to "系统",
    "getVolume" to "系统", "setVolume" to "系统",
    "getNetworkType" to "系统", "getStorageInfo" to "系统", "getScreenInfo" to "系统",
    "setClipboard" to "剪贴板", "getClipboard" to "剪贴板",
)

private val FUNCTION_ICONS: Map<String, String> = mapOf(
    "send_notification" to "🔔", "toast" to "💬",
    "create_automation" to "⏰",
    "getCurrentTime" to "🕐", "getBatteryLevel" to "🔋",
    "setScreenBrightness" to "☀️", "getDeviceInfo" to "📱",
    "getVolume" to "🔊", "setVolume" to "🔉",
    "getNetworkType" to "📶", "getStorageInfo" to "💾", "getScreenInfo" to "🖥️",
    "setClipboard" to "📋", "getClipboard" to "📄",
)

private val MCP_ICONS: Map<String, String> = mapOf(
    "weather_current" to "🌤", "translate_text" to "🌐",
    "news_headlines" to "📰", "geo_location" to "📍", "qrcode_generate" to "📱",
)

private data class McpToolItem(val name: String, val description: String)

@Composable
fun CapabilitiesScreen(
    builtinFunctions: List<FunctionInfo>,
    dexPluginInfos: List<FunctionInfo>,
    serverBaseUrl: String,
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf("") }
    var mcpTools by remember { mutableStateOf<List<McpToolItem>>(emptyList()) }
    var mcpLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val json = Json { ignoreUnknownKeys = true }
            val result = withContext(Dispatchers.IO) {
                val url = java.net.URL("$serverBaseUrl/api/v1/mcp/tools")
                url.readText()
            }
            val root = json.parseToJsonElement(result).jsonObject
            val items = root["data"]?.jsonArray?.map { el ->
                val obj = el.jsonObject
                McpToolItem(
                    name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                    description = obj["description"]?.jsonPrimitive?.contentOrNull ?: "",
                )
            } ?: emptyList()
            mcpTools = items
        } catch (_: Exception) {}
        mcpLoading = false
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("🧩 能力", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text("查看可用的 MCP 工具、DEX 插件和内置函数", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = filter, onValueChange = { filter = it },
            placeholder = { Text("搜索能力...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // MCP 工具
            val filteredMcp = mcpTools.filter { it.name.contains(filter, true) || it.description.contains(filter, true) }
            if (filteredMcp.isNotEmpty()) {
                item { SectionHeader("MCP 工具", filteredMcp.size) }
                items(filteredMcp, key = { "mcp_${it.name}" }) { tool ->
                    FunctionCard(icon = MCP_ICONS[tool.name] ?: "🔌", name = tool.name,
                        desc = tool.description, badge = "MCP", badgeColor = MaterialTheme.colorScheme.primary)
                }
            }

            // DEX 插件
            val filteredDex = dexPluginInfos.filter {
                it.name.contains(filter, true) || it.description.contains(filter, true)
            }
            if (filteredDex.isNotEmpty()) {
                item { SectionHeader("DEX 插件", filteredDex.size) }
                items(filteredDex, key = { "dex_${it.name}" }) { fn ->
                    FunctionCard(icon = "📦", name = fn.name, desc = fn.description,
                        badge = "DEX", badgeColor = MaterialTheme.colorScheme.tertiary)
                }
            }

            // 内置函数
            val filteredBuiltin = builtinFunctions.filter {
                it.name.contains(filter, true) || it.description.contains(filter, true)
            }
            if (filteredBuiltin.isNotEmpty()) {
                item { SectionHeader("内置函数", filteredBuiltin.size) }
                items(filteredBuiltin, key = { "fn_${it.name}" }) { fn ->
                    val cat = FUNCTION_CATEGORIES[fn.name]
                    FunctionCard(icon = FUNCTION_ICONS[fn.name] ?: "⚡", name = fn.name,
                        desc = fn.description, badge = cat ?: "本地")
                }
            }

            // Skill
            val filteredSkill = SKILLS.filter {
                it.name.contains(filter, true) || it.description.contains(filter, true)
            }
            if (filteredSkill.isNotEmpty()) {
                item { SectionHeader("Skill", filteredSkill.size) }
                items(filteredSkill, key = { "skill_${it.id}" }) { skill ->
                    SkillCard(skill)
                }
            }

            if (filteredMcp.isEmpty() && filteredDex.isEmpty() && filteredBuiltin.isEmpty() && filteredSkill.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center) {
                        Text("没有匹配的结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(4.dp))
        Text("($count)", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FunctionCard(icon: String, name: String, desc: String, badge: String, badgeColor: androidx.compose.ui.graphics.Color) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = MaterialTheme.typography.titleMedium.fontSize)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.width(4.dp))
                    Surface(color = badgeColor.copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.extraSmall) {
                        Text(badge, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor)
                    }
                }
                Text(desc, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SkillCard(skill: SkillItem) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(skill.icon, fontSize = MaterialTheme.typography.titleMedium.fontSize)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(skill.name, style = MaterialTheme.typography.titleSmall)
                        if (skill.cron != null) {
                            Spacer(Modifier.width(4.dp))
                            Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = MaterialTheme.shapes.extraSmall) {
                                Text(skill.cron, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Text(skill.description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                skill.steps.forEach { (fn, type) ->
                    Surface(color = if (type == "mcp") MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                        shape = MaterialTheme.shapes.extraSmall) {
                        Text("${if (type == "mcp") "🌐" else "📱"} $fn",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (type == "mcp") MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}

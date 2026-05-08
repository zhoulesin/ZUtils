package com.zhoulesin.zutils.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.zhoulesin.zutils.engine.core.FunctionInfo
import com.zhoulesin.zutils.mcp.McpClient
import com.zhoulesin.zutils.ui.theme.RaycastBlueTransparent
import com.zhoulesin.zutils.ui.theme.RaycastCardSurface
import com.zhoulesin.zutils.ui.theme.RaycastWhite
import com.zhoulesin.zutils.ui.theme.RaycastWhiteBorder06
import com.zhoulesin.zutils.ui.theme.RaycastWhiteBorder08

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
    "getCurrentTime" to "系统", "getDeviceInfo" to "系统",
    "getNetworkType" to "系统", "getStorageInfo" to "系统", "getScreenInfo" to "系统",
    "setClipboard" to "剪贴板", "getClipboard" to "剪贴板",
)

private val FUNCTION_ICONS: Map<String, String> = mapOf(
    "send_notification" to "🔔", "toast" to "💬",
    "create_automation" to "⏰",
    "getCurrentTime" to "🕐", "getDeviceInfo" to "📱",
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

    LaunchedEffect(serverBaseUrl) {
        val client = McpClient(baseUrl = serverBaseUrl)
        mcpTools = client.listTools().map { McpToolItem(name = it.name, description = it.description) }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("能力中心", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text("查看可用的 MCP 工具、DEX 插件和内置函数", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = filter, onValueChange = { filter = it },
            placeholder = { Text("搜索能力...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = RaycastWhiteBorder08,
                unfocusedContainerColor = MaterialTheme.colorScheme.background,
                focusedContainerColor = MaterialTheme.colorScheme.background,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )

        Spacer(Modifier.height(8.dp))

        val f = filter.lowercase()
        val filteredMcp by remember(mcpTools, f) { derivedStateOf {
            mcpTools.filter { it.name.contains(f) || it.description.contains(f) }
        }}
        val filteredDex by remember(dexPluginInfos, f) { derivedStateOf {
            dexPluginInfos.filter { it.name.contains(f) || it.description.contains(f) }
        }}
        val filteredBuiltin by remember(builtinFunctions, f) { derivedStateOf {
            builtinFunctions.filter { it.name.contains(f) || it.description.contains(f) }
        }}
        val filteredSkill by remember(f) { derivedStateOf {
            SKILLS.filter { it.name.contains(f) || it.description.contains(f) }
        }}

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (filteredMcp.isNotEmpty()) {
                item(key = "h_mcp") { SectionHeader("MCP 工具", filteredMcp.size) }
                items(
                    items = filteredMcp,
                    key = { "mcp_${it.name}" },
                    contentType = { "mcp_item" },
                ) { tool ->
                    FunctionCard(icon = MCP_ICONS[tool.name] ?: "🔌", name = tool.name,
                        desc = tool.description, badge = "MCP", badgeColor = MaterialTheme.colorScheme.primary)
                }
            }
            if (filteredDex.isNotEmpty()) {
                item(key = "h_dex") { SectionHeader("DEX 插件", filteredDex.size) }
                items(
                    items = filteredDex,
                    key = { "dex_${it.name}" },
                    contentType = { "dex_item" },
                ) { fn ->
                    FunctionCard(icon = "📦", name = fn.name, desc = fn.description,
                        badge = "DEX", badgeColor = MaterialTheme.colorScheme.tertiary)
                }
            }
            if (filteredBuiltin.isNotEmpty()) {
                item(key = "h_builtin") { SectionHeader("内置函数", filteredBuiltin.size) }
                items(
                    items = filteredBuiltin,
                    key = { "fn_${it.name}" },
                    contentType = { "builtin_item" },
                ) { fn ->
                    FunctionCard(icon = FUNCTION_ICONS[fn.name] ?: "⚡", name = fn.name,
                        desc = fn.description, badge = FUNCTION_CATEGORIES[fn.name] ?: "本地",
                        badgeColor = MaterialTheme.colorScheme.secondary)
                }
            }
            if (filteredSkill.isNotEmpty()) {
                item(key = "h_skill") { SectionHeader("Skill", filteredSkill.size) }
                items(
                    items = filteredSkill,
                    key = { "skill_${it.id}" },
                    contentType = { "skill_item" },
                ) { skill ->
                    SkillCard(skill)
                }
            }
            if (filteredMcp.isEmpty() && filteredDex.isEmpty() && filteredBuiltin.isEmpty() && filteredSkill.isEmpty()) {
                item(key = "empty") {
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
            color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(4.dp))
        Text("($count)", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FunctionCard(icon: String, name: String, desc: String, badge: String, badgeColor: androidx.compose.ui.graphics.Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, RaycastWhiteBorder06),
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = MaterialTheme.typography.titleMedium.fontSize)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.width(4.dp))
                    Surface(
                        color = RaycastCardSurface,
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, RaycastWhiteBorder06),
                    ) {
                        Text(
                            badge,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor,
                        )
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, RaycastWhiteBorder06),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(skill.icon, fontSize = MaterialTheme.typography.titleMedium.fontSize)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(skill.name, style = MaterialTheme.typography.titleSmall)
                        if (skill.cron != null) {
                            Spacer(Modifier.width(4.dp))
                            Surface(
                                color = RaycastCardSurface,
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, RaycastWhiteBorder06),
                            ) {
                                Text(skill.cron, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface)
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
                    Surface(
                        color = if (type == "mcp") RaycastBlueTransparent else RaycastCardSurface,
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, RaycastWhiteBorder06),
                    ) {
                        Text("${if (type == "mcp") "🌐" else "📱"} $fn",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (type == "mcp") MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

package com.zhoulesin.zutils

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.zhoulesin.zutils.agent.AgentExecution
import com.zhoulesin.zutils.compose.rememberRuntimePermissionRequester
import com.zhoulesin.zutils.agent.EntryType
import com.zhoulesin.zutils.agent.HistoryEntry
import com.zhoulesin.zutils.agent.ResultContent
import com.zhoulesin.zutils.config.ServerConfig
import com.zhoulesin.zutils.data.WorkflowStorage
import com.zhoulesin.zutils.engine.Engine
import com.zhoulesin.zutils.engine.llm.LlmClient
import com.zhoulesin.zutils.llm.ServerLlmClient
import com.zhoulesin.zutils.ui.screen.AutomationRulesScreen
import com.zhoulesin.zutils.ui.screen.CapabilitiesScreen
import com.zhoulesin.zutils.ui.screen.WorkflowBuilderScreen
import com.zhoulesin.zutils.ui.theme.RaycastBorder
import com.zhoulesin.zutils.ui.theme.RaycastWhite
import com.zhoulesin.zutils.ui.theme.RaycastWhiteBorder06
import com.zhoulesin.zutils.ui.theme.RaycastWhiteBorder08
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(engine: Engine, topBarTitle: String = "ZUtils") {
    var tab by remember { mutableStateOf(Tab.EXECUTE) }
    val history = remember { mutableStateListOf<HistoryEntry>() }
    val scope = rememberCoroutineScope()
    var showBuilder by remember { mutableStateOf(false) }
    val storage = remember { WorkflowStorage(engine.androidContext) }
    val serverBaseUrl = com.zhoulesin.zutils.config.ServerConfig.DEFAULT_BASE_URL
    val llmClient = remember { ServerLlmClient(serverBaseUrl) }
    val db = remember { com.zhoulesin.zutils.data.DatabaseProvider.get(engine.androidContext) }
    val autoDao = remember { db.automationRuleDao() }
    val autoEngine = remember { com.zhoulesin.zutils.engine.AutomationEngine(engine.androidContext, autoDao) }

    if (showBuilder) {
        WorkflowBuilderScreen(
            functions = engine.registry.getAllInfos(),
            onSave = { saved ->
                scope.launch {
                    storage.saveFromSteps(
                        id = saved.id, title = saved.title, desc = saved.description,
                        type = saved.type, steps = saved.steps,
                    )
                }
                showBuilder = false
            },
            onBack = { showBuilder = false },
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(topBarTitle, style = MaterialTheme.typography.titleMedium) },
                    actions = {},
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(RaycastBorder)
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
            ) {
                NavigationBarItem(
                    selected = tab == Tab.EXECUTE,
                    onClick = { tab = Tab.EXECUTE },
                    icon = { Text("▶") },
                    label = { Text("执行") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                NavigationBarItem(
                    selected = tab == Tab.CAPABILITIES,
                    onClick = { tab = Tab.CAPABILITIES },
                    icon = { Text("🧩") },
                    label = { Text("能力") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                NavigationBarItem(
                    selected = tab == Tab.AUTOMATION,
                    onClick = { tab = Tab.AUTOMATION },
                    icon = { Text("⚡") },
                    label = { Text("自动化") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    ) { padding ->
        when (tab) {
            Tab.EXECUTE -> ExecuteScreen(
                engine = engine,
                history = history,
                llmClient = llmClient,
                modifier = Modifier.padding(padding),
            )
            Tab.CAPABILITIES -> {
                var builtinInfos by remember { mutableStateOf(emptyList<com.zhoulesin.zutils.engine.core.FunctionInfo>()) }
                var dexInfos by remember { mutableStateOf(emptyList<com.zhoulesin.zutils.engine.core.FunctionInfo>()) }
                LaunchedEffect(engine) {
                    builtinInfos = engine.registry.getAllInfos()
                    try { dexInfos = engine.dexLoader?.getAllPluginInfos() ?: emptyList()
                    } catch (_: Exception) { dexInfos = emptyList() }
                }
                CapabilitiesScreen(
                    builtinFunctions = builtinInfos,
                    dexPluginInfos = dexInfos,
                    serverBaseUrl = serverBaseUrl,
                    modifier = Modifier.padding(padding),
                )
            }
            Tab.AUTOMATION -> AutomationRulesScreen(
                autoEngine = autoEngine,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

private enum class Tab { EXECUTE, CAPABILITIES, AUTOMATION }

@Composable
private fun ExecuteScreen(
    engine: Engine,
    history: MutableList<HistoryEntry>,
    llmClient: LlmClient?,
    modifier: Modifier,
) {
    var input by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    val requestRuntimePermissions = rememberRuntimePermissionRequester()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(history.toList()) { entry ->
                var expanded by remember { mutableStateOf(false) }
                val isError = entry.result is ResultContent.Text && entry.result.content.startsWith("Error")
                val lines = if (entry.result is ResultContent.Text) entry.result.content.lines() else emptyList()
                val summaryLine = lines.filter { it.contains("✅") }.lastOrNull()
                    ?: lines.firstOrNull { !it.startsWith("步骤") && !it.startsWith("总计") && !it.startsWith("  (") && it.isNotBlank() }
                    ?: ""
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isError) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        else RaycastWhiteBorder06,
                    ),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (entry.type == EntryType.TEXT) "> ${entry.label}" else entry.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            EntryTypeBadge(entry.type)
                        }
                        Spacer(Modifier.height(4.dp))
                        if (!expanded && entry.result is ResultContent.Text) {
                            Text(
                                text = summaryLine,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                            )
                            Text(
                                text = "展开详情 ▼",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (expanded) {
                            when (entry.result) {
                                is ResultContent.Text -> Text(
                                    text = entry.result.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                is ResultContent.QrImage -> {
                                    val bitmap = remember(entry.result.dataUri) {
                                        val base64 = entry.result.dataUri.removePrefix("data:image/png;base64,")
                                        val bytes = Base64.decode(base64, Base64.DEFAULT)
                                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    }
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "QR Code",
                                            modifier = Modifier
                                                .size(200.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Fit,
                                        )
                                    }
                                    Text(
                                        text = entry.result.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (entry.type == EntryType.WORKFLOW && entry.params.isNotEmpty()) {
                                Text(
                                    text = "入参: " + entry.params.entries.joinToString(", ") { "${it.key}=${it.value}" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
//                placeholder = { Text("输入需求，如：计算 2+2 / 时间 / 电量 / UUID / base64 编码hello / 音量50 / 剪贴板 / 屏幕 / 存储 / 网络") },
                placeholder = { Text("输入你的需求") },
                enabled = !isLoading,
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
            Button(
                onClick = {
                    val query = input.trim()
                    if (query.isNotEmpty()) {
                        input = ""
                        isLoading = true
                        history.add(0, HistoryEntry(query, EntryType.TEXT,
                            result = ResultContent.Text("🤖 Agent 启动中…")))
                        scope.launch {
                            val idx = 0
                            val result = AgentExecution.runQuery(
                                engine = engine,
                                query = query,
                                llmClient = llmClient,
                                onProgress = { text ->
                                    history[idx] = history[idx].copy(result = ResultContent.Text(text))
                                },
                                requestRuntimePermissions = requestRuntimePermissions,
                            )
                            history[idx] = history[idx].copy(result = result)
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading && input.isNotBlank(),
                shape = RoundedCornerShape(86.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, RaycastWhiteBorder08),
            ) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 40.dp, minWidth = 72.dp)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = RaycastWhite,
                        )
                    } else {
                        Text("执行", color = RaycastWhite)
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryTypeBadge(type: EntryType) {
    val (text, color) = when (type) {
        EntryType.TEXT -> "文本" to MaterialTheme.colorScheme.tertiary
        EntryType.WORKFLOW -> "工作流" to MaterialTheme.colorScheme.secondary
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


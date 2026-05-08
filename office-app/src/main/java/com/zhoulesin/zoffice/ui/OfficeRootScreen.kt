package com.zhoulesin.zoffice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhoulesin.zutils.agent.AgentExecution
import com.zhoulesin.zutils.agent.EntryType
import com.zhoulesin.zutils.agent.HistoryEntry
import com.zhoulesin.zutils.agent.ResultContent
import com.zhoulesin.zutils.compose.rememberRuntimePermissionRequester
import com.zhoulesin.zutils.config.ServerConfig
import com.zhoulesin.zutils.engine.Engine
import com.zhoulesin.zutils.engine.llm.LlmClient
import com.zhoulesin.zutils.llm.ServerLlmClient
import kotlinx.coroutines.launch

/**
 * ZOffice 专属界面：与 [:app] 主界面独立，仅复用引擎与 [AgentExecution]。
 */
private enum class OfficeTab {
    ASSISTANT,
    AUTOMATION,
    ME,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficeRootScreen(engine: Engine) {
    var tab by remember { mutableStateOf(OfficeTab.ASSISTANT) }
    val llmClient = remember { ServerLlmClient(ServerConfig.DEFAULT_BASE_URL) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ZOffice", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "一句话办事",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                NavigationBarItem(
                    selected = tab == OfficeTab.ASSISTANT,
                    onClick = { tab = OfficeTab.ASSISTANT },
                    icon = { Text("◎") },
                    label = { Text("助理") },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
                NavigationBarItem(
                    selected = tab == OfficeTab.AUTOMATION,
                    onClick = { tab = OfficeTab.AUTOMATION },
                    icon = { Text("⏱") },
                    label = { Text("自动化") },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
                NavigationBarItem(
                    selected = tab == OfficeTab.ME,
                    onClick = { tab = OfficeTab.ME },
                    icon = { Text("◉") },
                    label = { Text("我的") },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            }
        },
    ) { padding ->
        when (tab) {
            OfficeTab.ASSISTANT -> OfficeAssistantTab(
                engine = engine,
                llmClient = llmClient,
                modifier = Modifier.padding(padding),
            )
            OfficeTab.AUTOMATION -> OfficePlaceholderTab(
                title = "定时与规则",
                body = "将在此对接自动化规则与 WorkManager，与演示 App 的界面分离开发。",
                modifier = Modifier.padding(padding),
            )
            OfficeTab.ME -> OfficeMeTab(modifier = Modifier.padding(padding))
        }
    }
}

@Composable
private fun OfficeAssistantTab(
    engine: Engine,
    llmClient: LlmClient?,
    modifier: Modifier = Modifier,
) {
    val history = remember { mutableStateListOf<HistoryEntry>() }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val requestRuntimePermissions = rememberRuntimePermissionRequester()

    val quickPrompts = listOf(
        "用一句话写一封给团队的简短周报邮件草稿",
        "把明天下午3点开会记进日历",
        "总结一段话：本周完成了客户回访与排期调整",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            "今天想做哪件事？",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            quickPrompts.take(2).forEach { hint ->
                FilterChip(
                    selected = false,
                    onClick = { input = hint },
                    label = {
                        Text(
                            hint.take(18) + if (hint.length > 18) "…" else "",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(history.toList()) { entry ->
                OfficeResultCard(entry = entry)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                enabled = !loading,
                placeholder = { Text("说出或输入你的办公需求") },
                shape = RoundedCornerShape(16.dp),
                maxLines = 3,
            )
            Button(
                onClick = {
                    val q = input.trim()
                    if (q.isEmpty()) return@Button
                    input = ""
                    loading = true
                    history.add(
                        0,
                        HistoryEntry(q, EntryType.TEXT, result = ResultContent.Text("正在处理…")),
                    )
                    scope.launch {
                        val idx = 0
                        val result = AgentExecution.runQuery(
                            engine = engine,
                            query = q,
                            llmClient = llmClient,
                            onProgress = { progress ->
                                history[idx] = history[idx].copy(
                                    result = ResultContent.Text(progress),
                                )
                            },
                            requestRuntimePermissions = requestRuntimePermissions,
                        )
                        history[idx] = history[idx].copy(result = result)
                        loading = false
                    }
                },
                enabled = !loading && input.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Text("发送")
                }
            }
        }
    }
}

@Composable
private fun OfficeResultCard(entry: HistoryEntry) {
    val text = when (val r = entry.result) {
        is ResultContent.Text -> r.content
        is ResultContent.QrImage -> r.text + "\n(含二维码输出)"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                entry.label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun OfficePlaceholderTab(title: String, body: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OfficeMeTab(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text("ZOffice", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "本应用为 ZUtils 引擎的办公向产品壳，与「ZUtils」演示 App 界面相互独立。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

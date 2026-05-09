package com.zhoulesin.zoffice.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zhoulesin.zutils.agent.AgentExecution
import com.zhoulesin.zutils.agent.ResultContent
import com.zhoulesin.zutils.config.ServerConfig
import com.zhoulesin.zutils.data.CataloguePlugin
import com.zhoulesin.zutils.data.PluginInfo
import com.zhoulesin.zutils.data.PluginStorage
import com.zhoulesin.zutils.compose.rememberRuntimePermissionRequester
import com.zhoulesin.zutils.data.AppDatabase
import com.zhoulesin.zutils.data.DatabaseProvider
import com.zhoulesin.zutils.data.MessageEntity
import com.zhoulesin.zutils.data.SessionEntity
import com.zhoulesin.zutils.engine.AndroidAutomationEngine
import com.zhoulesin.zutils.engine.Engine
import com.zhoulesin.zutils.engine.llm.LlmClient
import com.zhoulesin.zutils.ui.screen.AutomationRulesScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private enum class OfficeTab {
    ASSISTANT,
    AUTOMATION,
    PLUGINS,
    ME,
}

data class MessageData(
    val id: String,
    val query: String,
    val displayText: String,
    val resultType: String,
    val timestamp: Long,
)

private fun MessageEntity.toMessageData() = MessageData(
    id = id, query = query, displayText = displayText,
    resultType = resultType, timestamp = timestamp,
)

private fun relativeTime(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000}分钟前"
        diff < 86_400_000 -> "${diff / 3600_000}小时前"
        else -> SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(ts))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficeRootScreen(engine: Engine) {
    var tab by remember { mutableStateOf(OfficeTab.ASSISTANT) }
    val llmClient = engine.llmClient
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val db = remember { DatabaseProvider.get(context) }
    val autoEngine = remember {
        AndroidAutomationEngine(context, db.automationRuleDao(), engine.registry)
    }

    val tts = remember {
        TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Toast.makeText(context, "TTS 初始化失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    var sessions by remember { mutableStateOf<List<SessionEntity>>(emptyList()) }
    var currentSessionId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        sessions = db.sessionDao().getAllList()
        if (sessions.isEmpty()) {
            val id = UUID.randomUUID().toString()
            db.sessionDao().insert(SessionEntity(id = id, title = "新会话"))
            sessions = db.sessionDao().getAllList()
            currentSessionId = id
        } else {
            currentSessionId = sessions.first().id
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("会话", style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = {
                            scope.launch {
                                val id = UUID.randomUUID().toString()
                                db.sessionDao().insert(SessionEntity(id = id, title = "新会话"))
                                sessions = db.sessionDao().getAllList()
                                currentSessionId = id
                                drawerState.close()
                            }
                        }) {
                            Text("+ 新建")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(sessions, key = { it.id }) { session ->
                            SessionListItem(
                                session = session,
                                isActive = session.id == currentSessionId,
                                onClick = {
                                    scope.launch {
                                        currentSessionId = session.id
                                        drawerState.close()
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        db.sessionDao().cascadeDeleteMessages(session.id)
                                        db.sessionDao().delete(session)
                                        sessions = db.sessionDao().getAllList()
                                        if (currentSessionId == session.id) {
                                            currentSessionId = sessions.firstOrNull()?.id
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        },
    ) {
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
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch { drawerState.open() }
                        }) {
                            Text("☰", style = MaterialTheme.typography.titleLarge)
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
                        selected = tab == OfficeTab.PLUGINS,
                        onClick = { tab = OfficeTab.PLUGINS },
                        icon = { Text("🧩") },
                        label = { Text("插件") },
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
                    db = db,
                    tts = tts,
                    sessions = sessions,
                    currentSessionId = currentSessionId,
                    onSessionsChange = { sessions = it },
                    onSessionIdChange = { currentSessionId = it },
                    modifier = Modifier.padding(padding),
                )
                OfficeTab.AUTOMATION -> OfficeAutomationTab(
                    autoEngine = autoEngine,
                    modifier = Modifier.padding(padding),
                )
                OfficeTab.PLUGINS -> OfficePluginsTab(
                    engine = engine,
                    modifier = Modifier.padding(padding),
                )
                OfficeTab.ME -> OfficeMeTab(modifier = Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun OfficeAssistantTab(
    engine: Engine,
    llmClient: LlmClient?,
    db: AppDatabase,
    tts: TextToSpeech,
    sessions: List<SessionEntity>,
    currentSessionId: String?,
    onSessionsChange: (List<SessionEntity>) -> Unit,
    onSessionIdChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var currentMessages by remember { mutableStateOf<List<MessageData>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val requestRuntimePermissions = rememberRuntimePermissionRequester()
    val assistantContext = LocalContext.current
    val recognizer = remember { SpeechRecognizer.createSpeechRecognizer(assistantContext) }

    DisposableEffect(Unit) {
        onDispose { recognizer.destroy() }
    }

    val voicePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINESE)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    isListening = false
                    val m = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!m.isNullOrEmpty()) input = m[0]
                }
                override fun onError(error: Int) { isListening = false }
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(p: Bundle?) {}
                override fun onEvent(t: Int, p: Bundle?) {}
            })
            isListening = true
            recognizer.startListening(intent)
        }
    }

    LaunchedEffect(currentSessionId) {
        currentSessionId?.let { id ->
            currentMessages = db.messageDao().getBySessionIdList(id).map { it.toMessageData() }
        }
    }

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
            items(currentMessages, key = { it.id }) { entry ->
                TypedResultCard(entry = entry, tts = tts)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
            IconButton(
                onClick = {
                    if (isListening) {
                        recognizer.stopListening()
                        isListening = false
                    } else if (ContextCompat.checkSelfPermission(assistantContext, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINESE)
                            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        }
                        recognizer.setRecognitionListener(object : RecognitionListener {
                            override fun onResults(results: Bundle?) {
                                isListening = false
                                val m = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                if (!m.isNullOrEmpty()) input = m[0]
                            }
                            override fun onError(error: Int) { isListening = false }
                            override fun onReadyForSpeech(p: Bundle?) {}
                            override fun onBeginningOfSpeech() {}
                            override fun onRmsChanged(v: Float) {}
                            override fun onBufferReceived(b: ByteArray?) {}
                            override fun onEndOfSpeech() {}
                            override fun onPartialResults(p: Bundle?) {}
                            override fun onEvent(t: Int, p: Bundle?) {}
                        })
                        isListening = true
                        recognizer.startListening(intent)
                    } else {
                        voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                enabled = !loading,
            ) {
                Text(if (isListening) "🔴" else "🎙", style = MaterialTheme.typography.titleMedium)
            }
            Button(
                onClick = {
                    val q = input.trim()
                    if (q.isEmpty() || currentSessionId == null) return@Button
                    input = ""
                    loading = true
                    scope.launch {
                        val sessionId = currentSessionId!!
                        val msgId = UUID.randomUUID().toString()

                        db.messageDao().insert(
                            MessageEntity(
                                id = msgId, sessionId = sessionId, query = q,
                                displayText = "", resultType = "processing",
                            )
                        )
                        currentMessages = currentMessages + MessageData(
                            msgId, q, "正在处理…", "processing", System.currentTimeMillis(),
                        )

                        val result = AgentExecution.runQuery(
                            engine = engine,
                            query = q,
                            llmClient = llmClient,
                            onProgress = { progress ->
                                currentMessages = currentMessages.map {
                                    if (it.id == msgId) it.copy(displayText = progress) else it
                                }
                            },
                            requestRuntimePermissions = requestRuntimePermissions,
                        )

                        val displayText = when (result) {
                            is ResultContent.Text -> result.content
                            is ResultContent.QrImage -> result.text + "\n(含二维码输出)"
                        }
                        val resultType = when (result) {
                            is ResultContent.Text -> result.resultType
                            is ResultContent.QrImage -> "image"
                        }

                        db.messageDao().insert(
                            MessageEntity(
                                id = msgId, sessionId = sessionId, query = q,
                                displayText = displayText, resultType = resultType,
                            )
                        )
                        db.sessionDao().incrementMessageCount(sessionId, System.currentTimeMillis())

                        val session = sessions.find { it.id == sessionId }
                        if (session != null && session.messageCount == 0) {
                            val title = q.take(25).trimEnd()
                            db.sessionDao().updateTitle(sessionId, title)
                        }

                        onSessionsChange(db.sessionDao().getAllList())
                        currentMessages = db.messageDao().getBySessionIdList(sessionId).map { it.toMessageData() }
                        loading = false
                    }
                },
                enabled = !loading && input.isNotBlank() && currentSessionId != null,
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
private fun TypedResultCard(entry: MessageData, tts: TextToSpeech) {
    val (icon, typeLabel, bgColor) = when (entry.resultType) {
        "weather" -> Triple("🌤", "天气", Color(0xFFE3F2FD))
        "calendar" -> Triple("📅", "日程", Color(0xFFE8F5E9))
        "contact" -> Triple("👤", "联系人", Color(0xFFFFF3E0))
        "file" -> Triple("📄", "文件", Color(0xFFEFEBE9))
        "error" -> Triple("❌", "错误", Color(0xFFFFEBEE))
        "processing" -> Triple("⏳", "处理中", Color(0xFFF5F5F5))
        else -> Triple("💬", "结果", Color(0xFFF5F5F5))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Text(typeLabel, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                if (entry.resultType != "processing" && entry.resultType != "error") {
                    IconButton(
                        onClick = {
                            tts.speak(entry.displayText, TextToSpeech.QUEUE_FLUSH, null, null)
                        },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Text("🔊", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(relativeTime(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                entry.query,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                entry.displayText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun OfficePluginsTab(
    engine: Engine,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var catalogue by remember { mutableStateOf<List<CataloguePlugin>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val storage = remember { PluginStorage(context) }
    var installed by remember { mutableStateOf(storage.loadAll()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val url = "${ServerConfig.DEFAULT_BASE_URL}/api/v1/plugins?size=50"
            val body = withContext(Dispatchers.IO) {
                val request = okhttp3.Request.Builder().url(url).get().build()
                okhttp3.OkHttpClient().newCall(request).execute().body?.string()
            }
            if (body != null) {
                val root = kotlinx.serialization.json.Json
                    .parseToJsonElement(body).jsonObject
                val data = root["data"]?.jsonObject
                val content = data?.get("content")?.jsonArray
                catalogue = content?.mapNotNull { element ->
                    val item = element.jsonObject
                    try {
                        CataloguePlugin(
                            id = item["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                            name = item["name"]?.jsonPrimitive?.content ?: "",
                            description = item["description"]?.jsonPrimitive?.content ?: "",
                            icon = item["icon"]?.jsonPrimitive?.content ?: "🧩",
                            category = item["category"]?.jsonPrimitive?.content ?: "工具",
                            version = item["version"]?.jsonPrimitive?.content ?: "1.0",
                            author = item["authorName"]?.jsonPrimitive?.content
                                ?: item["author"]?.jsonPrimitive?.content ?: "",
                            downloads = item["downloads"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            stepsJson = "",
                        )
                    } catch (_: Exception) { null }
                } ?: emptyList()
            }
        } catch (e: Exception) {
            error = e.message
        }
        loading = false
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("插件市场", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text("浏览并安装 DEX 插件", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载失败: $error", color = MaterialTheme.colorScheme.error)
            }
            catalogue.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无可用插件", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(catalogue) { plugin ->
                    val isInstalled = installed.any { it.id == plugin.id }
                    PluginMarketCard(
                        plugin = plugin,
                        isInstalled = isInstalled,
                        onInstall = {
                            scope.launch {
                                storage.save(PluginInfo(
                                    id = plugin.id, name = plugin.name,
                                    description = plugin.description, icon = plugin.icon,
                                    version = plugin.version, author = plugin.author,
                                    category = plugin.category, stepsJson = "",
                                ))
                                installed = storage.loadAll()
                            }
                        },
                        onUninstall = {
                            scope.launch {
                                storage.delete(plugin.id)
                                installed = storage.loadAll()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginMarketCard(
    plugin: CataloguePlugin,
    isInstalled: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(plugin.icon, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(plugin.name, style = MaterialTheme.typography.titleSmall)
                Text(plugin.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${plugin.author} · v${plugin.version}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isInstalled) {
                TextButton(onClick = onUninstall) {
                    Text("卸载", color = MaterialTheme.colorScheme.error)
                }
            } else {
                Button(onClick = onInstall) { Text("安装") }
            }
        }
    }
}

@Composable
private fun OfficeAutomationTab(
    autoEngine: AndroidAutomationEngine,
    modifier: Modifier = Modifier,
) {
    var showCreate by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        AutomationRulesScreen(autoEngine = autoEngine)

        FloatingActionButton(
            onClick = { showCreate = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
        ) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }

    if (showCreate) {
        CreateAutomationDialog(
            autoEngine = autoEngine,
            onDismiss = { showCreate = false },
        )
    }
}

@Composable
private fun CreateAutomationDialog(
    autoEngine: AndroidAutomationEngine,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var cron by remember { mutableStateOf("0 8 * * *") }
    var cronPreset by remember { mutableStateOf(0) }
    var stepsText by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val presets = listOf(
        "每天早上8点" to "0 8 * * *",
        "每1小时" to "0 * * * *",
        "每30分钟" to "*/30 * * * *",
        "每天中午12点" to "0 12 * * *",
        "自定义" to "",
    )

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("新建自动化规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("规则名称") },
                    placeholder = { Text("例如：早安天气") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("触发时间", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEachIndexed { i, (label, _) ->
                        FilterChip(
                            selected = cronPreset == i,
                            onClick = {
                                cronPreset = i
                                if (i < presets.size - 1) cron = presets[i].second
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                if (cronPreset == presets.size - 1) {
                    OutlinedTextField(
                        value = cron,
                        onValueChange = { cron = it },
                        label = { Text("Cron 表达式") },
                        placeholder = { Text("分 时 日 月 周") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Text("执行步骤", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = stepsText,
                    onValueChange = { stepsText = it },
                    label = { Text("函数名称，每行一个") },
                    placeholder = { Text("weather_current\nsend_notification") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    val steps = stepsText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    if (steps.isEmpty()) return@Button
                    saving = true
                    scope.launch {
                        try {
                            val stepList = steps.mapIndexed { index, fn ->
                                """{"function":"$fn","args":{},"pipeline":{}}"""
                            }
                            val stepsJson = "[${stepList.joinToString(",")}]"
                            autoEngine.create(name, cron, stepsJson)
                            Toast.makeText(context, "规则已创建", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } catch (e: Exception) {
                            Toast.makeText(context, "创建失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            saving = false
                        }
                    }
                },
                enabled = !saving && name.isNotBlank() && stepsText.isNotBlank(),
            ) {
                if (saving) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Text("创建")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun SessionListItem(
    session: SessionEntity,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isActive) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                session.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${session.messageCount}条消息 · ${relativeTime(session.updatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onDelete) {
            Text("删除", color = MaterialTheme.colorScheme.error)
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

package com.zhoulesin.zutils

import android.graphics.BitmapFactory
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.zhoulesin.zutils.data.InstalledPluginEntity
import com.zhoulesin.zutils.data.PluginInstallRepo
import com.zhoulesin.zutils.data.SavedWorkflow
import com.zhoulesin.zutils.data.WorkflowStorage
import com.zhoulesin.zutils.engine.Engine
import com.zhoulesin.zutils.engine.core.*
import com.zhoulesin.zutils.engine.functions.*
import com.zhoulesin.zutils.functions.time.GetCurrentTimeFunction
import com.zhoulesin.zutils.engine.llm.ChatMessage
import com.zhoulesin.zutils.engine.llm.ChatResult
import com.zhoulesin.zutils.engine.llm.LlmClient
import com.zhoulesin.zutils.llm.ServerLlmClient
import com.zhoulesin.zutils.engine.workflow.Workflow
import com.zhoulesin.zutils.engine.workflow.WorkflowResult
import com.zhoulesin.zutils.engine.workflow.WorkflowStep
import com.zhoulesin.zutils.plugin.DefaultDexLoader
import com.zhoulesin.zutils.ui.screen.CapabilitiesScreen
import com.zhoulesin.zutils.ui.screen.AutomationRulesScreen
import com.zhoulesin.zutils.ui.screen.WorkflowBuilderScreen
import com.zhoulesin.zutils.config.ServerConfig
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.zhoulesin.zutils.ui.theme.ZUtilsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.collections.emptyList

sealed class ResultContent {
    data class Text(val content: String) : ResultContent()
    data class QrImage(val dataUri: String, val text: String) : ResultContent()
    data class PermissionRequest(
        val permission: String,
        val message: String,
    ) : ResultContent()
}

enum class EntryType { TEXT, WORKFLOW }

data class HistoryEntry(
    val label: String,
    val type: EntryType,
    val params: Map<String, String> = emptyMap(),
    val result: ResultContent,
)

class MainActivity : ComponentActivity() {
    private lateinit var engine: Engine
    private lateinit var pluginRepo: PluginInstallRepo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pluginRepo = PluginInstallRepo(this)
        val db = com.zhoulesin.zutils.data.DatabaseProvider.get(this)
        val autoDao = db.automationRuleDao()
        val autoEngine = com.zhoulesin.zutils.engine.AutomationEngine(this, autoDao)

        engine = Engine(
            androidContext = this,
            dexLoader = DefaultDexLoader(
                this,
                remoteBaseUrl = "https://raw.githubusercontent.com/zhoulesin/ZUtils-plugins/main",
            ),
            onPluginLoaded = { name, version, className ->
                kotlinx.coroutines.MainScope().launch {
                    pluginRepo.save(name, version, className)
                }
            },
        ).also {
            // it.registry.register(CalculateFunction())  // 使用云端插件测试
            it.registry.register(GetCurrentTimeFunction())
            it.registry.register(GetBatteryLevelFunction())
            it.registry.register(SetScreenBrightnessFunction())
            it.registry.register(GetDeviceInfoFunction())
            it.registry.register(ToastFunction())
            it.registry.register(GetVolumeFunction())
            it.registry.register(SetVolumeFunction())
            it.registry.register(SetClipboardFunction())
            it.registry.register(GetClipboardFunction())
            it.registry.register(GetScreenInfoFunction())
            it.registry.register(GetStorageInfoFunction())
            it.registry.register(GetNetworkTypeFunction())
            it.registry.register(SendNotificationFunction())
            it.registry.register(CreateAutomationFunction(autoEngine))
        }

        // Reschedule all enabled automation rules on startup
        kotlinx.coroutines.MainScope().launch {
            autoEngine.rescheduleAll()
        }

        kotlinx.coroutines.MainScope().launch {
            engine.dexLoader?.let { pluginRepo.loadCachedPlugins(it, engine.registry) }
        }

        setContent {
            ZUtilsTheme {
                MainScreen(engine = engine)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(engine: Engine) {
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
        topBar = {
            TopAppBar(
                title = { Text("ZUtils AI Engine") },
                actions = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.EXECUTE,
                    onClick = { tab = Tab.EXECUTE },
                    icon = { Text("▶") },
                    label = { Text("执行") },
                )
                NavigationBarItem(
                    selected = tab == Tab.CAPABILITIES,
                    onClick = { tab = Tab.CAPABILITIES },
                    icon = { Text("🧩") },
                    label = { Text("能力") },
                )
                NavigationBarItem(
                    selected = tab == Tab.AUTOMATION,
                    onClick = { tab = Tab.AUTOMATION },
                    icon = { Text("⚡") },
                    label = { Text("自动化") },
                )
            }
        }
    ) { padding ->
        when (tab) {
            Tab.EXECUTE -> ExecuteScreen(engine, history, llmClient, Modifier.padding(padding))
            Tab.CAPABILITIES -> {
                var dexInfos by remember { mutableStateOf(emptyList<com.zhoulesin.zutils.engine.core.FunctionInfo>()) }
                LaunchedEffect(Unit) {
                    dexInfos = engine.dexLoader?.getAllPluginInfos() ?: emptyList()
                }
                CapabilitiesScreen(
                    builtinFunctions = engine.registry.getAllInfos(),
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
    var pendingPermission by remember { mutableStateOf<String?>(null) }

    if (pendingPermission != null) {
        AlertDialog(
            onDismissRequest = { pendingPermission = null },
            title = { Text("需要授权") },
            text = { Text("执行该功能需要「修改系统设置」权限。是否前往系统设置页面开启？") },
            confirmButton = {
                TextButton(onClick = {
                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = Uri.parse("package:${engine.androidContext.packageName}")
                    }
                    engine.androidContext.startActivity(intent)
                    pendingPermission = null
                }) { Text("去授权") }
            },
            dismissButton = {
                TextButton(onClick = { pendingPermission = null }) { Text("取消") }
            },
        )
    }

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
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
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
                                is ResultContent.PermissionRequest -> {
                                    Text(
                                        text = entry.result.message,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = { pendingPermission = entry.result.permission },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    ) { Text("去授权") }
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
                            val result = runQuery(engine, query, llmClient) { text ->
                                history[idx] = history[idx].copy(result = ResultContent.Text(text))
                            }
                            history[idx] = history[idx].copy(result = result)
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading && input.isNotBlank(),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("执行")
                }
            }
        }
    }
}

private suspend fun runQueryRaw(engine: Engine, workflow: Workflow): ResultContent {
    val result = engine.execute(workflow)
    return formatResult(result)
}

private suspend fun runQuery(
    engine: Engine, query: String, llmClient: LlmClient?,
    onProgress: ((String) -> Unit)? = null,
): ResultContent {
    val logs = StringBuilder()
    logs.appendLine("🤖 Agent 执行记录")
    logs.appendLine("━━━━━━━━━━━━━━━━")
    fun push(msg: String) {
        logs.appendLine(msg); Log.i("ZUtils-LLM", msg)
        onProgress?.invoke(logs.toString())
    }

    push("📥 输入: \"$query\"")

    if (llmClient == null) {
        return runQueryRaw(engine, parseQuery(query))
    }

    val messages = mutableListOf(ChatMessage(role = "user", content = query))
    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    val httpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    var turn = 0
    var maxTurns = 10
    while (maxTurns-- > 0) {
        turn++
        push("")
        push("── 第 ${turn} 轮 ──")
        push("💭 思考中...")
        val result = llmClient.chat(messages, engine.getAllAvailableInfos())

        when (result) {
            is ChatResult.ToolCall -> {
                val fn = result.function
                val argsStr = result.args.toString()
                push("🔧 调用: $fn")
                if (argsStr.length < 100) push("   参数: $argsStr")
                val output = executeMcpCall(httpClient, json, fn, result.args)
                push("✅ 结果: ${output.take(150)}${if (output.length > 150) "…" else ""}")
                messages.add(ChatMessage(role = "user",
                    content = "$fn 的返回结果：$output\n\n根据结果决定下一步，如果任务完成请总结回复用户。"))
            }
            is ChatResult.FinalAnswer -> {
                push("💬 ${result.text}")
                logs.appendLine("━━━━━━━━━━━━━━━━")
                logs.append("✅ 最终回答:\n${result.text}")
                return ResultContent.Text(logs.toString())
            }
            is ChatResult.Error -> {
                push("⚠️ Agent 错误: ${result.message}")
                return runQueryRaw(engine, parseQuery(query))
            }
        }
    }
    push("⏰ 执行超时")
    return ResultContent.Text(logs.toString())
}

private suspend fun executeMcpCall(
    client: okhttp3.OkHttpClient,
    json: kotlinx.serialization.json.Json,
    function: String,
    args: kotlinx.serialization.json.JsonObject,
): String = withContext(Dispatchers.IO) {
    val bodyJson = kotlinx.serialization.json.buildJsonObject {
        put("tool", function)
        put("arguments", args)
    }
    val request = okhttp3.Request.Builder()
        .url("${ServerConfig.DEFAULT_BASE_URL}/api/v1/mcp/call")
        .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
        .build()
    val response = client.newCall(request).execute()
    val body = response.body?.string() ?: ""
    val root = json.parseToJsonElement(body).jsonObject
    root["data"]?.jsonObject?.get("output")?.jsonPrimitive?.contentOrNull ?: body
}

private fun parseQuery(query: String): Workflow {
    val q = query.lowercase().trim()

    val steps = when {
        q.startsWith("计算") || q.startsWith("calc") || q.matches(Regex("^[\\d+\\-*/().\\s]+$")) -> {
            val expr = if (q.matches(Regex("^[\\d+\\-*/().\\s]+$"))) q
            else q.replace(Regex("^计算|^calc[a-z]*\\s*", RegexOption.IGNORE_CASE), "").trim()
                .ifEmpty { "0" }
            listOf(step("calculate", "expression" to expr))
        }
        q.contains("时间") || q.contains("几点") || q.contains("time") -> {
            listOf(step("getCurrentTime", "format" to "yyyy-MM-dd HH:mm:ss"))
        }
        q.contains("电量") || q.contains("电池") || q.contains("battery") -> {
            listOf(step("getBatteryLevel"))
        }
        q.contains("亮度") || q.contains("brightness") -> {
            val level = Regex("""(\d+)""").find(q)?.groupValues?.get(1)?.toIntOrNull()
                ?: 50
            listOf(step("setScreenBrightness", "level" to level.toString()))
        }
        q.contains("设备") || q.contains("device") || q.contains("手机信息") -> {
            listOf(step("getDeviceInfo"))
        }
        q.contains("toast") || q.contains("提示") -> {
            val msg = q.removePrefix("toast").removePrefix("提示").trim()
                .ifEmpty { "Hello from ZUtils!" }
            listOf(step("toast", "message" to msg))
        }
        q.contains("uuid") || q.contains("随机码") -> {
            listOf(step("uuid"))
        }
        q.contains("base64") -> {
            val isEncode = !q.contains("解码") && !q.contains("decode")
            val text = q.replace("base64", "").replace("编码", "").replace("解码", "").trim()
                .ifEmpty { "hello" }
            listOf(step("base64", "action" to if (isEncode) "encode" else "decode", "text" to text))
        }

        q.contains("音量") || q.contains("volume") -> {
            val level = Regex("""(\d+)""").find(q)?.groupValues?.get(1)?.toIntOrNull()
            if (level != null) {
                listOf(step("setVolume", "level" to level.toString()))
            } else {
                listOf(step("getVolume"))
            }
        }
        q.contains("剪贴板") || q.contains("clipboard") -> {
            if (q.contains("复制") || q.contains("写入") || q.contains("set")) {
                val text = q.replace("复制", "").replace("写入", "").replace("剪贴板", "").replace("clipboard", "").trim()
                    .ifEmpty { "Copied by ZUtils" }
                listOf(step("setClipboard", "text" to text))
            } else {
                listOf(step("getClipboard"))
            }
        }
        q.contains("屏幕") || q.contains("分辨率") || q.contains("screen") -> {
            listOf(step("getScreenInfo"))
        }
        q.contains("存储") || q.contains("空间") || q.contains("storage") -> {
            listOf(step("getStorageInfo"))
        }
        q.contains("网络") || q.contains("wifi") || q.contains("network") || q.contains("流量") -> {
            listOf(step("getNetworkType"))
        }
        q.contains("连续") || q.contains("然后") -> {
            mutableListOf<WorkflowStep>().apply {
                if (q.contains("计算") || q.contains("calc")) {
                    val m = Regex("""计算\s*([\d+\-*/().\s]+)""").find(query)
                    val expr = m?.groupValues?.get(1)?.trim() ?: "1+1"
                    add(step("calculate", "expression" to expr))
                }
                if (q.contains("时间") || q.contains("time")) {
                    add(step("getCurrentTime"))
                }
                if (q.contains("电量")) {
                    add(step("getBatteryLevel"))
                }
                if (q.contains("音量")) {
                    add(step("getVolume"))
                }
                if (q.contains("网络")) {
                    add(step("getNetworkType"))
                }
                if (q.contains("uuid")) {
                    add(step("uuid"))
                }
                if (q.contains("toast")) {
                    add(step("toast", "message" to "All done!"))
                }
                if (isEmpty()) add(step("getDeviceInfo"))
            }
        }
        else -> {
            listOf(step("getDeviceInfo"))
        }
    }

    return Workflow(steps = steps)
}

private fun step(name: String, vararg args: Pair<String, String>): WorkflowStep {
    val jsonArgs = if (args.isEmpty()) JsonObject(emptyMap<String, JsonElement>())
    else JsonObject(args.associate { (k, v) -> k to JsonPrimitive(v) })
    return WorkflowStep(function = name, args = jsonArgs)
}

private suspend fun formatResult(result: WorkflowResult): ResultContent {
    val sb = StringBuilder()
    var dataUri: String? = null
    var hasImage = false
    var permissionError: String? = null

    result.dexLoadLog?.forEach { line ->
        sb.appendLine("  $line")
    }

    for ((i, step) in result.steps.withIndex()) {
        sb.appendLine("步骤 ${i + 1}: ${step.function}")
        when (val r = step.result) {
            is ZResult.Success -> {
                if (r.mediaType == MediaType.IMAGE_PNG) {
                    hasImage = true
                    dataUri = r.data.toString().let { json ->
                        Regex("data:image/png;base64,[a-zA-Z0-9+/=]+")
                            .find(json)?.value
                    }
                }
                sb.appendLine("  ✅ ${r.data}")
            }
            is ZResult.Error -> {
                if (r.code == "MISSING_PERMISSION") {
                    permissionError = r.message
                }
                sb.appendLine("  ❌ ${r.message}")
            }
        }
        if (step.durationMs > 0) {
            sb.appendLine("  (${step.durationMs}ms)")
        }
    }
    sb.appendLine("总计: ${result.totalDurationMs}ms")
    val text = sb.toString().trimEnd()

    if (permissionError != null) {
        return ResultContent.PermissionRequest(
            permission = "android.permission.WRITE_SETTINGS",
            message = text,
        )
    }

    return if (hasImage && dataUri != null) {
        ResultContent.QrImage(dataUri = dataUri, text = text)
    } else {
        ResultContent.Text(text)
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

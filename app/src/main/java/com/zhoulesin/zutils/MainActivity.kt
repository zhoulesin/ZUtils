package com.zhoulesin.zutils

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
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
import com.zhoulesin.zutils.engine.Engine
import com.zhoulesin.zutils.engine.core.*
import com.zhoulesin.zutils.plugin.DefaultDexLoader
import com.zhoulesin.zutils.engine.functions.*
import com.zhoulesin.zutils.engine.workflow.Workflow
import com.zhoulesin.zutils.engine.workflow.WorkflowResult
import com.zhoulesin.zutils.engine.workflow.WorkflowStep
import com.zhoulesin.zutils.ui.theme.ZUtilsTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

sealed class ResultContent {
    data class Text(val content: String) : ResultContent()
    data class QrImage(val dataUri: String, val text: String) : ResultContent()
}

class MainActivity : ComponentActivity() {
    private lateinit var engine: Engine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        engine = Engine(
            androidContext = this,
            dexLoader = DefaultDexLoader(this),
        ).also {
            it.registry.register(CalculateFunction())
            it.registry.register(GetCurrentTimeFunction())
            it.registry.register(GetBatteryLevelFunction())
            it.registry.register(SetScreenBrightnessFunction())
            it.registry.register(GetDeviceInfoFunction())
            it.registry.register(ToastFunction())
            it.registry.register(UuidFunction())
            it.registry.register(Base64Function())
            it.registry.register(GetVolumeFunction())
            it.registry.register(SetVolumeFunction())
            it.registry.register(SetClipboardFunction())
            it.registry.register(GetClipboardFunction())
            it.registry.register(GetScreenInfoFunction())
            it.registry.register(GetStorageInfoFunction())
            it.registry.register(GetNetworkTypeFunction())
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
    var input by remember { mutableStateOf("") }
    val history = remember { mutableStateListOf<Pair<String, ResultContent>>() }
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ZUtils AI Engine") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(history.toList()) { (query, content) ->
                    val isError = content is ResultContent.Text && content.content.startsWith("Error")
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isError) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "> $query",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(4.dp))
                            when (content) {
                                is ResultContent.Text -> Text(
                                    text = content.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                is ResultContent.QrImage -> {
                                    val bitmap = remember(content.dataUri) {
                                        val base64 = content.dataUri.removePrefix("data:image/png;base64,")
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
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = content.text,
                                        style = MaterialTheme.typography.bodySmall,
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
                    placeholder = { Text("输入需求，如：计算 2+2 / 时间 / 电量 / UUID / base64 编码hello / 音量50 / 剪贴板 / 屏幕 / 存储 / 网络") },
                    enabled = !isLoading,
                    singleLine = true,
                )
                Button(
                    onClick = {
                        val query = input.trim()
                        if (query.isNotEmpty()) {
                            input = ""
                            isLoading = true
                            scope.launch {
                                val result = runQuery(engine, query)
                                history.add(0, query to result)
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
}

private suspend fun runQuery(engine: Engine, query: String): ResultContent {
    val workflow = parseQuery(query)
    val result = engine.execute(workflow)
    return formatResult(result)
}

private fun parseQuery(query: String): Workflow {
    val q = query.lowercase().trim()

    val steps = when {
        q.startsWith("计算") || q.startsWith("calc") -> {
            val expr = q.removePrefix("计算").removePrefix("calc").trim()
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
        q.contains("二维码") || q.contains("qrcode") || q.contains("qr") -> {
            val content = q
                .replace("二维码", "").replace("qrcode", "").replace("qr", "")
                .trim().ifEmpty { "https://github.com/zhoulesin/ZUtils" }
            val size = Regex("""(\d+)""").find(q)?.groupValues?.get(1)?.toIntOrNull() ?: 300
            listOf(step("generateQRCode", "content" to content, "size" to size.toString()))
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
            is ZResult.Error -> sb.appendLine("  ❌ ${r.message}")
        }
        if (step.durationMs > 0) {
            sb.appendLine("  (${step.durationMs}ms)")
        }
    }
    sb.appendLine("总计: ${result.totalDurationMs}ms")
    val text = sb.toString().trimEnd()

    return if (hasImage && dataUri != null) {
        ResultContent.QrImage(dataUri = dataUri, text = text)
    } else {
        ResultContent.Text(text)
    }
}

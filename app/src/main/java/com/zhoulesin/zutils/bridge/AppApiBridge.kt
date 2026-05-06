package com.zhoulesin.zutils.bridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.zhoulesin.zutils.engine.core.ExecutionContext
import com.zhoulesin.zutils.engine.core.ZResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * ApiBridge 的 Android 宿主实现。
 * App 启动时调用 init()，所有 DEX 插件通过此桥间接调用安卓能力。
 */
object AppApiBridge : ApiBridge {

    lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    override fun callApi(apiTag: String, params: List<String>): String {
        return when (apiTag) {
            "folder_create" -> handleFolderCreate(params)
            "file_write" -> handleFileWrite(params)
            "clipboard_copy" -> handleClipboardCopy(params)
            else -> "未知能力标识：$apiTag"
        }
    }

    private fun handleFolderCreate(params: List<String>): String {
        if (params.isEmpty()) return "参数缺失：文件夹名"
        val root = File(appContext.filesDir, "AIWorkSpace")
        val target = File(root, params[0])
        return if (target.exists()) {
            "文件夹已存在:${target.absolutePath}"
        } else {
            if (target.mkdirs()) "创建成功:${target.absolutePath}" else "创建失败"
        }
    }

    private fun handleFileWrite(params: List<String>): String {
        if (params.size < 3) return "参数缺失：文件夹名/文件名/内容"
        val root = File(appContext.filesDir, "AIWorkSpace")
        val folder = File(root, params[0]).also { if (!it.exists()) it.mkdirs() }
        val file = File(folder, params[1])
        return try {
            file.writeText(params[2])
            "文件写入成功:${file.absolutePath}"
        } catch (e: Exception) {
            "写入失败:${e.message}"
        }
    }

    private fun handleClipboardCopy(params: List<String>): String {
        if (params.isEmpty()) return "参数缺失：复制内容"
        val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("AIWork", params[0]))
        return "已复制到剪贴板"
    }
}

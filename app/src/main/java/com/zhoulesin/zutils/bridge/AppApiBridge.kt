package com.zhoulesin.zutils.bridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import java.io.File

/**
 * ApiBridge 的 Android 宿主实现。
 *
 * 只暴露底层安卓 API，不包含任何业务逻辑。
 * 业务逻辑在 Playground Kotlin 代码中完成。
 */
object AppApiBridge : ApiBridge {

    lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    override fun callApi(apiTag: String, params: List<String>): String {
        return when (apiTag) {
            "get_files_dir" -> appContext.filesDir.absolutePath
            "read_file" -> readFile(params)
            "write_file" -> writeFile(params)
            "file_exists" -> fileExists(params)
            "delete_file" -> deleteFile(params)
            "mkdirs" -> mkdirs(params)
            "clipboard_copy" -> clipboardCopy(params)
            "clipboard_paste" -> clipboardPaste()
            "show_toast" -> showToast(params)
            else -> "未知 API: $apiTag"
        }
    }

    private fun readFile(params: List<String>): String {
        if (params.isEmpty()) return "参数缺失：路径"
        return try { File(params[0]).readText() } catch (e: Exception) { "读取失败: ${e.message}" }
    }

    private fun writeFile(params: List<String>): String {
        if (params.size < 2) return "参数缺失：路径/内容"
        return try {
            File(params[0]).writeText(params[1])
            "写入成功"
        } catch (e: Exception) { "写入失败: ${e.message}" }
    }

    private fun fileExists(params: List<String>): String {
        if (params.isEmpty()) return "参数缺失：路径"
        return File(params[0]).exists().toString()
    }

    private fun deleteFile(params: List<String>): String {
        if (params.isEmpty()) return "参数缺失：路径"
        return try { File(params[0]).delete().toString() } catch (e: Exception) { "删除失败: ${e.message}" }
    }

    private fun mkdirs(params: List<String>): String {
        if (params.isEmpty()) return "参数缺失：路径"
        return try { File(params[0]).mkdirs().toString() } catch (e: Exception) { "创建失败: ${e.message}" }
    }

    private fun clipboardCopy(params: List<String>): String {
        if (params.isEmpty()) return "参数缺失：内容"
        val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("ai", params[0]))
        return "已复制"
    }

    private fun clipboardPaste(): String {
        val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cm.primaryClip?.getItemAt(0)?.text?.toString() ?: "(空)"
    }

    private fun showToast(params: List<String>): String {
        if (params.isEmpty()) return "参数缺失：内容"
        android.widget.Toast.makeText(appContext, params[0], android.widget.Toast.LENGTH_SHORT).show()
        return "已显示"
    }
}

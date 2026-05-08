package com.zhoulesin.zutils.engine.functions

import android.content.Intent
import androidx.core.content.FileProvider
import com.zhoulesin.zutils.engine.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.File

class ShareFileFunction : ZFunction {
    override val info = FunctionInfo(
        name = "shareFile",
        description = "通过系统分享面板分享文件",
        parameters = listOf(
            Parameter("path", "要分享的文件路径", ParameterType.STRING, required = true),
        ),
        outputType = OutputType.NONE,
    )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult = withContext(Dispatchers.IO) {
        try {
            val path = args["path"]?.toString()?.removeSurrounding("\"")
                ?: return@withContext ZResult.fail("缺少文件路径")
            val file = File(path)
            if (!file.exists()) return@withContext ZResult.fail("文件不存在: $path")

            val uri = FileProvider.getUriForFile(
                context.androidContext,
                "${context.androidContext.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.androidContext.startActivity(Intent.createChooser(intent, "分享文件"))
            ZResult.ok("已打开分享面板")
        } catch (e: Exception) {
            ZResult.fail("分享文件失败: ${e.message}", "SHARE_ERROR")
        }
    }
}

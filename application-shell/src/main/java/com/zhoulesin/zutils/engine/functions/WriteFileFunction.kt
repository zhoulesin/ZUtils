package com.zhoulesin.zutils.engine.functions

import com.zhoulesin.zutils.engine.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WriteFileFunction : ZFunction {
    override val info = FunctionInfo(
        name = "writeFile",
        description = "将文本内容写入文件。路径留空则自动创建到 Download/ 目录，以时间戳命名",
        parameters = listOf(
            Parameter("content", "要写入的文本内容", ParameterType.STRING, required = true),
            Parameter("path", "目标文件路径（选填，不填自动生成）", ParameterType.STRING),
            Parameter("filename", "文件名（选填，不填自动以时间戳命名）", ParameterType.STRING),
        ),
        outputType = OutputType.OBJECT,
    )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult = withContext(Dispatchers.IO) {
        try {
            val content = args["content"]?.toString()?.removeSurrounding("\"")
                ?: return@withContext ZResult.fail("缺少写入内容")
            var path = args["path"]?.toString()?.removeSurrounding("\"")
            val filename = args["filename"]?.toString()?.removeSurrounding("\"")

            if (path.isNullOrBlank()) {
                val dir = File(context.androidContext.getExternalFilesDir(null), "documents")
                dir.mkdirs()
                val name = filename ?: "note_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.txt"
                path = File(dir, name).absolutePath
            }

            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(content)
            ZResult.ok("文件已保存: $path")
        } catch (e: Exception) {
            ZResult.fail("写入文件失败: ${e.message}", "FILE_WRITE_ERROR")
        }
    }
}

package com.zhoulesin.zutils.functions

import com.zhoulesin.zutils.engine.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

class ReadFileFunction : ZFunction {
    override val info = FunctionInfo(
        name = "readFile",
        description = "读取手机存储中的文本文件内容。支持绝对路径或文件名（仅文件名自动到 documents 目录查找）",
        parameters = listOf(
            Parameter("path", "文件路径（绝对路径如 /storage/emulated/0/Download/note.txt，或仅文件名如 note.txt）", ParameterType.STRING, required = true),
            Parameter("encoding", "编码（默认 UTF-8）", ParameterType.STRING),
        ),
        outputType = OutputType.TEXT,
    )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult = withContext(Dispatchers.IO) {
        try {
            var path = args["path"]?.toString()?.removeSurrounding("\"")
                ?: return@withContext ZResult.fail("缺少文件路径")
            val file = resolveFile(context, path)
            if (!file.exists()) return@withContext ZResult.fail("文件不存在: $path")
            if (!file.canRead()) return@withContext ZResult.fail("无法读取文件: $path")
            val content = file.readText()
            ZResult.ok(content)
        } catch (e: Exception) {
            ZResult.fail("读取文件失败: ${e.message}", "FILE_READ_ERROR")
        }
    }

    private fun resolveFile(context: ExecutionContext, path: String): File {
        val file = File(path)
        if (file.isAbsolute) return file
        val dir = File(context.androidContext.getExternalFilesDir(null), "documents")
        return File(dir, path)
    }
}

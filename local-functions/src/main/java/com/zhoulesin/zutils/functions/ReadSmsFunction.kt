package com.zhoulesin.zutils.functions

import android.database.Cursor
import android.provider.Telephony
import com.zhoulesin.zutils.engine.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class ReadSmsFunction : ZFunction {
    override val info = FunctionInfo(
        name = "readSms",
        description = "读取系统收件箱中的短信，返回最近 N 条",
        parameters = listOf(
            Parameter("count", "读取条数（默认5）", ParameterType.NUMBER),
            Parameter("sender", "按发件人号码筛选（选填）", ParameterType.STRING),
        ),
        permissions = listOf(android.Manifest.permission.READ_SMS),
        outputType = OutputType.OBJECT,
    )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult = withContext(Dispatchers.IO) {
        try {
            val count = args["count"]?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: 5
            val senderFilter = args["sender"]?.toString()?.removeSurrounding("\"")

            val selection = if (!senderFilter.isNullOrBlank()) {
                "${Telephony.Sms.Inbox.ADDRESS} LIKE ?"
            } else null
            val selectionArgs = if (selection != null) arrayOf("%$senderFilter%") else null

            val cursor: Cursor? = context.androidContext.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(
                    Telephony.Sms.Inbox._ID,
                    Telephony.Sms.Inbox.ADDRESS,
                    Telephony.Sms.Inbox.BODY,
                    Telephony.Sms.Inbox.DATE,
                ),
                selection,
                selectionArgs,
                "${Telephony.Sms.Inbox.DATE} DESC",
            )

            if (cursor == null) {
                return@withContext ZResult.fail("无法访问短信数据库", "SMS_ACCESS_DENIED")
            }

            val results = mutableListOf<JsonElement>()
            var index = 0
            while (cursor.moveToNext() && index < count) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.Inbox._ID))
                val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.Inbox.ADDRESS))
                val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.Inbox.BODY))
                val dateMillis = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.Inbox.DATE))

                results.add(buildJsonObject {
                    put("id", JsonPrimitive(id))
                    put("sender", JsonPrimitive(address ?: ""))
                    put("body", JsonPrimitive(body ?: ""))
                    put("date", JsonPrimitive(java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(dateMillis))))
                })
                index++
            }
            cursor.close()

            if (results.isEmpty()) {
                ZResult.ok("收件箱中无短信")
            } else {
                ZResult.Success(JsonArray(results))
            }
        } catch (e: SecurityException) {
            ZResult.fail("缺少读取短信权限", "PERMISSION_DENIED", recoverable = true)
        } catch (e: Exception) {
            ZResult.fail("读取短信失败: ${e.message}", "SMS_ERROR")
        }
    }
}

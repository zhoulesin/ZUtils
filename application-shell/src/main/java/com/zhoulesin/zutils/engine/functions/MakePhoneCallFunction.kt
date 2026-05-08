package com.zhoulesin.zutils.engine.functions

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.zhoulesin.zutils.engine.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

class MakePhoneCallFunction : ZFunction {
    override val info = FunctionInfo(
        name = "makePhoneCall",
        description = "拨打电话。提供 contactName 自动查通讯录，或直接提供 phoneNumber",
        parameters = listOf(
            Parameter("contactName", "联系人姓名（与 phoneNumber 二选一）", ParameterType.STRING),
            Parameter("phoneNumber", "电话号码（与 contactName 二选一）", ParameterType.STRING),
        ),
        permissions = listOf(android.Manifest.permission.READ_CONTACTS),
        outputType = OutputType.TEXT,
    )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult = withContext(Dispatchers.IO) {
        try {
            val contactName = args["contactName"]?.toString()?.removeSurrounding("\"")
            var phoneNumber = args["phoneNumber"]?.toString()?.removeSurrounding("\"")

            if (contactName != null && contactName.isNotBlank() && (phoneNumber.isNullOrBlank())) {
                val number = lookupPhone(context, contactName)
                if (number != null) phoneNumber = number
                else return@withContext ZResult.fail("未找到联系人「$contactName」的电话号码")
            }

            if (phoneNumber.isNullOrBlank()) {
                return@withContext ZResult.fail("请指定联系人姓名或电话号码")
            }

            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${phoneNumber}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.androidContext.startActivity(intent)
            ZResult.ok("已打开拨号面板：$phoneNumber")
        } catch (e: SecurityException) {
            ZResult.fail("缺少通讯录权限", "PERMISSION_DENIED", recoverable = true)
        } catch (e: Exception) {
            ZResult.fail("拨号失败: ${e.message}", "PHONE_ERROR")
        }
    }

    private fun lookupPhone(ctx: ExecutionContext, name: String): String? {
        val cursor = ctx.androidContext.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        ) ?: return null
        return cursor.use {
            if (it.moveToFirst()) {
                it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
            } else null
        }
    }
}

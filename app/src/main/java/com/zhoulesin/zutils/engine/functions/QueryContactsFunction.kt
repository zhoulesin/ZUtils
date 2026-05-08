package com.zhoulesin.zutils.engine.functions

import android.annotation.SuppressLint
import android.provider.ContactsContract
import com.zhoulesin.zutils.engine.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.Locale

class QueryContactsFunction : ZFunction {
    override val info = FunctionInfo(
        name = "queryContacts",
        description = "根据姓名搜索系统通讯录中的联系人",
        parameters = listOf(
            Parameter("name", "联系人姓名（模糊搜索）", ParameterType.STRING, required = true),
            Parameter("limit", "最多返回条数（默认10）", ParameterType.NUMBER),
        ),
        permissions = listOf(android.Manifest.permission.READ_CONTACTS),
        outputType = OutputType.OBJECT,
    )

    @SuppressLint("Range")
    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult = withContext(Dispatchers.IO) {
        try {
            val name = args["name"]?.toString()?.removeSurrounding("\"")
                ?: return@withContext ZResult.fail("缺少搜索姓名")
            val limit = args["limit"]?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: 10

            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.HAS_PHONE_NUMBER,
            )
            val selection = "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?"
            val cursor = context.androidContext.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI, projection, selection,
                arrayOf("%$name%"), "${ContactsContract.Contacts.DISPLAY_NAME} ASC"
            ) ?: return@withContext ZResult.ok("[]")

            val results = mutableListOf<JsonElement>()
            var count = 0
            while (cursor.moveToNext() && count < limit) {
                val contactId = cursor.getLong(cursor.getColumnIndex(ContactsContract.Contacts._ID))
                val displayName = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME))
                val hasPhone = cursor.getInt(cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0

                val phones = if (hasPhone) {
                    val phoneCursor = context.androidContext.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(contactId.toString()), null
                    )
                    val list = mutableListOf<String>()
                    phoneCursor?.use {
                        while (it.moveToNext()) {
                            list.add(it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)))
                        }
                    }
                    list
                } else emptyList()

                val emails = run {
                    val emailCursor = context.androidContext.contentResolver.query(
                        ContactsContract.CommonDataKinds.Email.CONTENT_URI, null,
                        "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
                        arrayOf(contactId.toString()), null
                    )
                    val list = mutableListOf<String>()
                    emailCursor?.use {
                        while (it.moveToNext()) {
                            list.add(it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)))
                        }
                    }
                    list
                }

                results.add(buildJsonObject {
                    put("id", JsonPrimitive(contactId))
                    put("name", JsonPrimitive(displayName))
                    put("phones", JsonArray(phones.map { JsonPrimitive(it) }))
                    put("emails", JsonArray(emails.map { JsonPrimitive(it) }))
                })
                count++
            }
            cursor.close()

            if (results.isEmpty()) {
                ZResult.ok("未找到匹配「$name」的联系人")
            } else {
                ZResult.Success(JsonArray(results))
            }
        } catch (e: SecurityException) {
            ZResult.fail("缺少通讯录权限", "PERMISSION_DENIED", recoverable = true)
        } catch (e: Exception) {
            ZResult.fail("查询联系人失败: ${e.message}", "CONTACTS_ERROR")
        }
    }
}

package com.zhoulesin.zutils.engine.functions

import android.annotation.SuppressLint
import android.content.ContentValues
import android.provider.CalendarContract
import com.zhoulesin.zutils.engine.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.TimeZone

class CreateCalendarEventFunction : ZFunction {
    override val info = FunctionInfo(
        name = "createCalendarEvent",
        description = "在系统日历中创建日程事件",
        parameters = listOf(
            Parameter("title", "事件标题", ParameterType.STRING, required = true),
            Parameter("startTime", "开始时间 ISO 格式，如 2026-05-10T15:00", ParameterType.STRING),
            Parameter("endTime", "结束时间 ISO 格式（选填，默认开始+1小时）", ParameterType.STRING),
            Parameter("location", "地点（选填）", ParameterType.STRING),
            Parameter("description", "事件描述（选填）", ParameterType.STRING),
            Parameter("reminderMinutes", "提前多少分钟提醒（默认15）", ParameterType.NUMBER),
        ),
        permissions = listOf(android.Manifest.permission.WRITE_CALENDAR),
        outputType = OutputType.OBJECT,
    )

    @SuppressLint("MissingPermission")
    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult = withContext(Dispatchers.IO) {
        try {
            val title = args["title"]?.toString()?.removeSurrounding("\"") ?: return@withContext ZResult.fail("缺少事件标题")
            val startStr = args["startTime"]?.toString()?.removeSurrounding("\"")
            val endStr = args["endTime"]?.toString()?.removeSurrounding("\"")
            val location = args["location"]?.toString()?.removeSurrounding("\"")
            val desc = args["description"]?.toString()?.removeSurrounding("\"")
            val remindMin = args["reminderMinutes"]?.toString()?.removeSurrounding("\"")?.toLongOrNull() ?: 15L

            val startTime = java.time.OffsetDateTime.parse(startStr ?: java.time.OffsetDateTime.now().plusHours(1).toString())
            val endTime = endStr?.let { java.time.OffsetDateTime.parse(it) } ?: startTime.plusHours(1)

            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, 1)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, desc ?: "")
                put(CalendarContract.Events.EVENT_LOCATION, location ?: "")
                put(CalendarContract.Events.DTSTART, startTime.toInstant().toEpochMilli())
                put(CalendarContract.Events.DTEND, endTime.toInstant().toEpochMilli())
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.HAS_ALARM, 1)
            }

            val uri = context.androidContext.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                ?: return@withContext ZResult.fail("创建日历事件失败")

            val eventId = uri.lastPathSegment?.toLongOrNull()

            if (eventId != null) {
                val reminderValues = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                    put(CalendarContract.Reminders.MINUTES, remindMin)
                }
                context.androidContext.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
            }

            ZResult.ok("日程「$title」已创建，开始于 ${startTime.toLocalTime()}，提前 $remindMin 分钟提醒")
        } catch (e: SecurityException) {
            ZResult.fail("缺少日历权限", "PERMISSION_DENIED", recoverable = true)
        } catch (e: Exception) {
            ZResult.fail("创建日历事件失败: ${e.message}", "CALENDAR_ERROR")
        }
    }
}

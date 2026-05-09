package com.zhoulesin.zutils.functions

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import com.zhoulesin.zutils.engine.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.zhoulesin.zutils.contract.ZutilsDateTimeParse
import kotlinx.serialization.json.JsonObject
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.TimeZone

class CreateCalendarEventFunction : ZFunction {
    override val info = FunctionInfo(
        name = "createCalendarEvent",
        description = "在系统日历中创建日程事件",
        parameters = listOf(
            Parameter("title", "事件标题", ParameterType.STRING, required = true),
            Parameter(
                "startTime",
                "见 ZUtils 日期时间约定 docs/contracts/zutils-datetime-strings.md：无偏移如 2026-05-10T15:00 表示设备本地；或 2026-05-10T15:00+08:00 / 2026-05-10T07:00Z",
                ParameterType.STRING,
            ),
            Parameter(
                "endTime",
                "同 startTime（选填，默认开始+1小时）",
                ParameterType.STRING,
            ),
            Parameter("location", "地点（选填）", ParameterType.STRING),
            Parameter("description", "事件描述（选填）", ParameterType.STRING),
            Parameter("reminderMinutes", "提前多少分钟提醒（默认15）", ParameterType.NUMBER),
        ),
        permissions = listOf(
            android.Manifest.permission.WRITE_CALENDAR,
            android.Manifest.permission.READ_CALENDAR,
        ),
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

            val zone = ZoneId.systemDefault()
            val startTime = ZutilsDateTimeParse.parse(
                startStr ?: OffsetDateTime.now(zone).plusHours(1).toString(),
                zone,
            )
            val endTime = endStr?.let { ZutilsDateTimeParse.parse(it, zone) } ?: startTime.plusHours(1)

            val calendarId = findWritableCalendarId(context.androidContext)
                ?: return@withContext ZResult.fail(
                    "未找到可写入的系统日历。请在「日历」应用中添加并启用至少一个账户（如 Google/Exchange/本地日历）后再试。",
                    "CALENDAR_NO_WRITABLE",
                    recoverable = true,
                )

            val values = ContentValues().apply {
                put(Events.CALENDAR_ID, calendarId)
                put(Events.TITLE, title)
                put(Events.DESCRIPTION, desc ?: "")
                put(Events.EVENT_LOCATION, location ?: "")
                put(Events.DTSTART, startTime.toInstant().toEpochMilli())
                put(Events.DTEND, endTime.toInstant().toEpochMilli())
                put(Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(Events.HAS_ALARM, 1)
            }

            val uri = context.androidContext.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                ?: return@withContext ZResult.fail(
                    "日历写入被拒绝（contentResolver.insert 返回空）。请确认已授予日历读写权限且系统日历服务正常。",
                    "CALENDAR_INSERT_NULL",
                    recoverable = true,
                )

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
            val detail = e.message?.takeIf { it.isNotBlank() }
                ?: e.cause?.message?.takeIf { it.isNotBlank() }
                ?: e.javaClass.simpleName
            ZResult.fail("创建日历事件失败: $detail", "CALENDAR_ERROR")
        }
    }

    /**
     * 解析可见且具备写权限的日历；避免误用硬编码 `CALENDAR_ID = 1`。
     */
    private fun findWritableCalendarId(ctx: Context): Long? {
        val projection = arrayOf(Calendars._ID, Calendars.CALENDAR_ACCESS_LEVEL)
        val selection = "${Calendars.VISIBLE} = 1"
        var cursor: Cursor? = null
        try {
            cursor = ctx.contentResolver.query(
                Calendars.CONTENT_URI,
                projection,
                selection,
                null,
                "${Calendars.IS_PRIMARY} DESC, ${Calendars._ID} ASC",
            ) ?: return null
            val idCol = cursor.getColumnIndex(Calendars._ID)
            val accessCol = cursor.getColumnIndex(Calendars.CALENDAR_ACCESS_LEVEL)
            if (idCol < 0 || accessCol < 0) return null
            while (cursor.moveToNext()) {
                when (cursor.getInt(accessCol)) {
                    Calendars.CAL_ACCESS_OWNER,
                    Calendars.CAL_ACCESS_EDITOR,
                    Calendars.CAL_ACCESS_CONTRIBUTOR,
                    -> return cursor.getLong(idCol)
                    else -> { }
                }
            }
        } finally {
            cursor?.close()
        }
        return null
    }
}

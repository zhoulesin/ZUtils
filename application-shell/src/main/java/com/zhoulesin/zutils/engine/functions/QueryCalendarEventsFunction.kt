package com.zhoulesin.zutils.engine.functions

import android.annotation.SuppressLint
import android.provider.CalendarContract
import com.zhoulesin.zutils.engine.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class QueryCalendarEventsFunction : ZFunction {
    override val info = FunctionInfo(
        name = "queryCalendarEvents",
        description = "查询系统日历中指定日期的日程事件。不传日期则查今天",
        parameters = listOf(
            Parameter("date", "日期，格式 yyyy-MM-dd（选填，默认今天）", ParameterType.STRING),
            Parameter("days", "查询天数（选填，默认1天）", ParameterType.NUMBER),
        ),
        permissions = listOf(android.Manifest.permission.READ_CALENDAR),
        outputType = OutputType.OBJECT,
    )

    @SuppressLint("MissingPermission")
    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult = withContext(Dispatchers.IO) {
        try {
            val dateStr = args["date"]?.toString()?.removeSurrounding("\"")
            val days = args["days"]?.toString()?.removeSurrounding("\"")?.toLongOrNull() ?: 1L

            val zone = ZoneId.systemDefault()
            val startDate = if (dateStr != null && dateStr.isNotBlank()) {
                LocalDate.parse(dateStr)
            } else {
                LocalDate.now(zone)
            }
            val startMillis = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
            val endMillis = startDate.plusDays(days).atStartOfDay(zone).toInstant().toEpochMilli()

            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(startMillis.toString())
                .appendPath(endMillis.toString())
                .build()

            val projection = arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
            )

            val cursor = context.androidContext.contentResolver.query(
                uri, projection, null, null,
                "${CalendarContract.Instances.BEGIN} ASC"
            ) ?: return@withContext ZResult.ok("[]")

            val events = mutableListOf<JsonElement>()
            cursor.use {
                while (it.moveToNext()) {
                    val title = it.getString(it.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)) ?: ""
                    val desc = it.getString(it.getColumnIndexOrThrow(CalendarContract.Instances.DESCRIPTION)) ?: ""
                    val location = it.getString(it.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)) ?: ""
                    val begin = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN))
                    val end = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Instances.END))
                    val allDay = it.getInt(it.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)) != 0

                    val beginTime = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(begin), zone)
                    val endTime = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(end), zone)

                    events.add(buildJsonObject {
                        put("title", JsonPrimitive(title))
                        if (desc.isNotBlank()) put("description", JsonPrimitive(desc))
                        if (location.isNotBlank()) put("location", JsonPrimitive(location))
                        put("startTime", JsonPrimitive(beginTime.toString()))
                        put("endTime", JsonPrimitive(endTime.toString()))
                        put("allDay", JsonPrimitive(allDay))
                    })
                }
            }

            if (events.isEmpty()) {
                ZResult.ok("$startDate 没有日程安排")
            } else {
                val summary = "$startDate 共 ${events.size} 个日程"
                ZResult.Success(buildJsonObject {
                    put("summary", JsonPrimitive(summary))
                    put("events", JsonArray(events))
                })
            }
        } catch (e: SecurityException) {
            ZResult.fail("缺少日历读取权限", "PERMISSION_DENIED", recoverable = true)
        } catch (e: Exception) {
            ZResult.fail("查询日历失败: ${e.message}", "CALENDAR_QUERY_ERROR")
        }
    }
}

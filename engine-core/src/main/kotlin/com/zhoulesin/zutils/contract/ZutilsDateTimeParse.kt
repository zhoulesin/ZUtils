package com.zhoulesin.zutils.contract

import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * ZUtils 统一的日期时间字符串解析；约定见仓库根目录
 * `docs/contracts/zutils-datetime-strings.md`，JVM 侧对应
 * `com.zutils.server.util.ZutilsDateTimeStrings`。
 */
object ZutilsDateTimeParse {

    /**
     * @param naiveLocalZone 当字符串无 UTC 偏移时，用于解释墙钟时间（设备上一般为 [ZoneId.systemDefault]）。
     */
    fun parse(text: String, naiveLocalZone: ZoneId): OffsetDateTime {
        val t = text.trim()
        return try {
            OffsetDateTime.parse(t)
        } catch (_: DateTimeException) {
            LocalDateTime.parse(t).atZone(naiveLocalZone).toOffsetDateTime()
        }
    }
}

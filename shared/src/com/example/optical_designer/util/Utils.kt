package com.example.optical_designer.util

expect fun currentTimeMillis(): Long

expect fun generateUuid(): String

fun formatDate(timestampMs: Long): String {
    if (timestampMs <= 0) return "Unknown"
    // Simple epoch timestamp formatting helper
    val seconds = timestampMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    // Epoch day calculation for date display: YYYY-MM-DD format
    // 1970-01-01 was Thursday (Day 0)
    var z = days + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = mp + (if (mp < 10) 3 else -9)
    val year = y + (if (m <= 2) 1 else 0)

    val hourOfDay = (hours % 24).toInt()
    val minuteOfHour = (minutes % 60).toInt()

    fun pad(n: Long, width: Int = 2): String = n.toString().padStart(width, '0')
    fun padInt(n: Int, width: Int = 2): String = n.toString().padStart(width, '0')

    return "${pad(year, 4)}-${pad(m, 2)}-${pad(d, 2)} ${padInt(hourOfDay)}:${padInt(minuteOfHour)}"
}

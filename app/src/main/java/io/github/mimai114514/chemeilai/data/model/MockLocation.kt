package io.github.mimai114514.chemeilai.data.model

import kotlinx.serialization.Serializable

@Serializable
data class MockLocation(
    val lat: Double,
    val lng: Double,
    val savedAt: Long = 0L,
    val label: String? = null,
) {
    fun toText(): String = label?.takeIf { it.isNotBlank() }
        ?.let { "$it,$lat,$lng" }
        ?: "$lat,$lng"
}

/**
 * 定时切换规则：到点后切换到 [useMock] 指定的定位来源。
 * [days] 用 ISO 星期（1=周一 … 7=周日），为空表示每天。
 */
@Serializable
data class MockSwitchRule(
    val id: String,
    val hour: Int,
    val minute: Int,
    val days: List<Int> = emptyList(),
    val useMock: Boolean = true,
    val enabled: Boolean = true,
) {
    val minutesOfDay: Int get() = hour * 60 + minute

    fun matchesDay(isoDay: Int): Boolean = days.isEmpty() || isoDay in days

    fun timeText(): String = "%02d:%02d".format(hour, minute)

    fun daysText(): String = if (days.isEmpty()) {
        "每天"
    } else {
        days.sorted().joinToString(" ") { WEEKDAY_LABELS.getOrElse(it - 1) { it.toString() } }
    }

    companion object {
        val WEEKDAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    }
}

package com.example.on_device_qwen

import com.example.on_device_qwen.model.ConversationSession
import com.example.on_device_qwen.ui.components.DrawerSectionLabels
import com.example.on_device_qwen.ui.components.formatDrawerTelemetry
import com.example.on_device_qwen.ui.components.groupConversationsByDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class DrawerGroupingTests {

    private val utc: ZoneId = ZoneId.of("UTC")

    private fun session(
        id: String,
        updatedAt: Long,
        isPinned: Boolean = false,
    ) = ConversationSession(
        id = id,
        title = "Chat $id",
        createdAt = updatedAt,
        updatedAt = updatedAt,
        isPinned = isPinned,
    )

    @Test
    fun emptyInputReturnsEmptyGrouping() {
        val result = groupConversationsByDate(emptyList(), nowMillis = 1_700_000_000_000L, zoneId = utc)
        assertTrue(result.isEmpty())
    }

    @Test
    fun groupsPinnedAndAllDateBucketsInCanonicalOrder() {
        val now = ZonedDateTime.of(2026, 6, 15, 14, 30, 0, 0, utc)
        val nowMillis = now.toInstant().toEpochMilli()
        val todayStart = now.toLocalDate().atStartOfDay(utc).toInstant().toEpochMilli()
        val yesterdayStart = now.toLocalDate().minusDays(1).atStartOfDay(utc).toInstant().toEpochMilli()
        val sevenDaysAgoStart = now.toLocalDate().minusDays(7).atStartOfDay(utc).toInstant().toEpochMilli()

        // Intentionally supply items in reverse/jumbled chronological order.
        val input = listOf(
            session("older", sevenDaysAgoStart - 1L),
            session("pinned-old", sevenDaysAgoStart - 86_400_000L, isPinned = true),
            session("prev7-lower-bound", sevenDaysAgoStart),
            session("yesterday-upper", todayStart - 1L),
            session("today-start", todayStart),
            session("yesterday-start", yesterdayStart),
            session("pinned-new", nowMillis - 1_000L, isPinned = true),
            session("today-now", nowMillis),
            session("prev7-upper", yesterdayStart - 1L),
        )

        val grouped = groupConversationsByDate(input, nowMillis = nowMillis, zoneId = utc)

        assertEquals(
            listOf(
                DrawerSectionLabels.PINNED,
                DrawerSectionLabels.TODAY,
                DrawerSectionLabels.YESTERDAY,
                DrawerSectionLabels.PREVIOUS_7_DAYS,
                DrawerSectionLabels.OLDER,
            ),
            grouped.keys.toList(),
        )

        assertEquals(listOf("pinned-new", "pinned-old"), grouped[DrawerSectionLabels.PINNED]?.map { it.id })
        assertEquals(listOf("today-now", "today-start"), grouped[DrawerSectionLabels.TODAY]?.map { it.id })
        assertEquals(listOf("yesterday-upper", "yesterday-start"), grouped[DrawerSectionLabels.YESTERDAY]?.map { it.id })
        assertEquals(
            listOf("prev7-upper", "prev7-lower-bound"),
            grouped[DrawerSectionLabels.PREVIOUS_7_DAYS]?.map { it.id },
        )
        assertEquals(listOf("older"), grouped[DrawerSectionLabels.OLDER]?.map { it.id })
    }

    @Test
    fun omitsEmptySectionsAndRespectsDstDayBoundaries() {
        // America/New_York springs forward on 2026-03-08 (a 23-hour day).
        val ny = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(2026, 3, 9, 10, 0, 0, 0, ny)
        val nowMillis = now.toInstant().toEpochMilli()

        // 2026-03-08 00:30 EST is the start of yesterday's local calendar day (23 hours before 2026-03-09 00:00 EDT).
        val yesterdayMorning = ZonedDateTime.of(2026, 3, 8, 0, 30, 0, 0, ny).toInstant().toEpochMilli()
        val twoDaysAgoNight = ZonedDateTime.of(2026, 3, 7, 23, 59, 0, 0, ny).toInstant().toEpochMilli()

        val grouped = groupConversationsByDate(
            conversations = listOf(
                session("yday", yesterdayMorning),
                session("prev7", twoDaysAgoNight),
            ),
            nowMillis = nowMillis,
            zoneId = ny,
        )

        assertEquals(
            listOf(DrawerSectionLabels.YESTERDAY, DrawerSectionLabels.PREVIOUS_7_DAYS),
            grouped.keys.toList(),
        )
        assertEquals(listOf("yday"), grouped[DrawerSectionLabels.YESTERDAY]?.map { it.id })
        assertEquals(listOf("prev7"), grouped[DrawerSectionLabels.PREVIOUS_7_DAYS]?.map { it.id })
    }

    @Test
    fun formatsTelemetryStripAcrossTotalRamAndFreeRamModes() {
        assertEquals(
            "RAM 4.2/8.0 GB · 36.4°C · 84%",
            formatDrawerTelemetry(
                availableRamMb = 4301L,
                totalRamMb = 8192L,
                batteryTemp = 36.4f,
                batteryPercent = 84,
            ),
        )

        assertEquals(
            "RAM 3.5 GB free · 29.0°C · 100%",
            formatDrawerTelemetry(
                availableRamMb = 3584L,
                totalRamMb = 0L,
                batteryTemp = 29.0f,
                batteryPercent = 120,
            ),
        )

        assertEquals(
            "RAM 512 MB free · 31.2°C · 0%",
            formatDrawerTelemetry(
                availableRamMb = 512L,
                totalRamMb = 0L,
                batteryTemp = 31.2f,
                batteryPercent = -5,
            ),
        )
    }
}

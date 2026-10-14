package com.reelcounter.app.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

interface Clock {
    fun nowMs(): Long
    fun zone(): ZoneId
}

object SystemClock : Clock {
    override fun nowMs(): Long = System.currentTimeMillis()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

object Retention {
    /** Rolling 30 × 24h from the current instant — not 30 calendar dates (PRD §7). */
    val WINDOW_MS: Long = TimeUnit.DAYS.toMillis(30)

    fun cutoff(nowMs: Long): Long = nowMs - WINDOW_MS
}

object Days {
    /** Local-day key using the zone captured at event time (PRD §5.5 rollover rule). */
    fun key(epochMs: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate().toString()

    fun key(date: LocalDate): String = date.toString()

    fun today(clock: Clock): LocalDate = Instant.ofEpochMilli(clock.nowMs()).atZone(clock.zone()).toLocalDate()

    fun startOfDayMs(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfDayMs(date: LocalDate, zone: ZoneId): Long = startOfDayMs(date.plusDays(1), zone)
}

package com.seventyfivehard.tracker.data

import kotlinx.serialization.Serializable

@Serializable
data class AppData(
    val schemaVersion: Int = 1,
    val current: Attempt? = null,
    val history: List<Attempt> = emptyList(),
    val settings: Settings = Settings(),
)

@Serializable
data class Attempt(
    val number: Int,
    /** ISO date (yyyy-MM-dd) of Day 1. */
    val startDate: String,
    /** Logs keyed by ISO date. Days with nothing logged are absent. */
    val days: Map<String, DayLog> = emptyMap(),
    val dietPlan: String = "",
    val endDate: String? = null,
    val endReason: EndReason? = null,
)

@Serializable
enum class EndReason { FAILED, COMPLETED, ABANDONED }

@Serializable
data class Workout(
    val done: Boolean = false,
    val activity: String = "",
)

@Serializable
data class DayLog(
    val workout1: Workout = Workout(),
    val workout2Outdoor: Workout = Workout(),
    val dietFollowed: Boolean = false,
    val noAlcohol: Boolean = false,
    val waterMl: Int = 0,
    val pagesRead: Int = 0,
    val bookTitle: String = "",
    /** File name inside the app's private photos directory. */
    val photoFile: String? = null,
    val note: String = "",
    /** Day completed before the app was installed ("I'm already on day X"). */
    val backfilled: Boolean = false,
)

@Serializable
enum class WaterUnit { ML, OZ }

@Serializable
data class Settings(
    val waterUnit: WaterUnit = WaterUnit.ML,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val appLock: Boolean = false,
)

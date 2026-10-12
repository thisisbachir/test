package com.seventyfivehard.tracker.domain

import com.seventyfivehard.tracker.data.Attempt
import com.seventyfivehard.tracker.data.DayLog
import com.seventyfivehard.tracker.data.WaterUnit
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

object Rules {
    const val TOTAL_DAYS = 75
    // 1 US gallon is 3785 ml; a few ml of slack so 8/16/32 oz taps (rounded to ml) add up.
    const val WATER_GOAL_ML = 3780
    const val PAGES_GOAL = 10
    const val ML_PER_OZ = 29.5735
}

enum class Task(val title: String, val subtitle: String) {
    WORKOUT_1("Workout #1", "45 minutes, any kind"),
    WORKOUT_2_OUTDOOR("Workout #2 · outdoors", "45 minutes, must be outside"),
    DIET("Follow your diet", "No cheat meals"),
    NO_ALCOHOL("No alcohol", "Not a single drink"),
    WATER("Drink 1 gallon of water", "3.8 L / 128 oz"),
    READING("Read 10 pages", "Non-fiction, no audiobooks"),
    PHOTO("Progress photo", "One every day");

    fun isDone(log: DayLog): Boolean = log.backfilled || when (this) {
        WORKOUT_1 -> log.workout1.done
        WORKOUT_2_OUTDOOR -> log.workout2Outdoor.done
        DIET -> log.dietFollowed
        NO_ALCOHOL -> log.noAlcohol
        WATER -> log.waterMl >= Rules.WATER_GOAL_ML
        READING -> log.pagesRead >= Rules.PAGES_GOAL
        PHOTO -> log.photoFile != null
    }
}

fun DayLog.isComplete(): Boolean = Task.entries.all { it.isDone(this) }
fun DayLog.doneCount(): Int = Task.entries.count { it.isDone(this) }

val Attempt.start: LocalDate get() = LocalDate.parse(startDate)
val Attempt.lastDay: LocalDate get() = start.plusDays(Rules.TOTAL_DAYS - 1L)

fun Attempt.log(date: LocalDate): DayLog = days[date.toString()] ?: DayLog()

fun Attempt.withLog(date: LocalDate, log: DayLog): Attempt =
    copy(days = days + (date.toString() to log))

/** 1-based day number of [date] within this attempt (may be out of 1..75). */
fun Attempt.dayNumber(date: LocalDate): Int = ChronoUnit.DAYS.between(start, date).toInt() + 1

fun Attempt.dateOfDay(day: Int): LocalDate = start.plusDays(day - 1L)

fun Attempt.contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(lastDay)

fun Attempt.completedDays(): Int =
    (1..Rules.TOTAL_DAYS).count { log(dateOfDay(it)).isComplete() }

sealed interface AttemptStatus {
    data class NotStarted(val startsOn: LocalDate) : AttemptStatus
    data class Active(val day: Int) : AttemptStatus
    /** A day before today was left incomplete: by the rules, back to Day 1. */
    data class Missed(val date: LocalDate, val day: Int) : AttemptStatus
    data object Completed : AttemptStatus
}

fun Attempt.status(today: LocalDate): AttemptStatus {
    if (today.isBefore(start)) return AttemptStatus.NotStarted(start)
    val checkUntil = minOf(today.minusDays(1), lastDay)
    var d = start
    while (!d.isAfter(checkUntil)) {
        if (!log(d).isComplete()) return AttemptStatus.Missed(d, dayNumber(d))
        d = d.plusDays(1)
    }
    // Every day before today is done; finished once day 75 is done too.
    if (log(lastDay).isComplete() && !today.isBefore(lastDay)) return AttemptStatus.Completed
    return AttemptStatus.Active(dayNumber(today))
}

/** Consecutive complete days ending today (or yesterday if today isn't done yet). */
fun Attempt.streak(today: LocalDate): Int {
    var d = minOf(today, lastDay)
    if (!log(d).isComplete()) d = d.minusDays(1)
    var count = 0
    while (!d.isBefore(start) && log(d).isComplete()) {
        count++
        d = d.minusDays(1)
    }
    return count
}

enum class DayState { COMPLETE, MISSED, TODAY, UPCOMING }

fun Attempt.dayState(date: LocalDate, today: LocalDate): DayState = when {
    log(date).isComplete() -> DayState.COMPLETE
    date == today -> DayState.TODAY
    date.isBefore(today) -> DayState.MISSED
    else -> DayState.UPCOMING
}

data class Totals(
    val waterMl: Long,
    val pages: Int,
    val workouts: Int,
    val outdoorWorkouts: Int,
    val photos: Int,
)

fun Attempt.totals(): Totals {
    val logs = days.values.filter { !it.backfilled }
    return Totals(
        waterMl = logs.sumOf { it.waterMl.toLong() },
        pages = logs.sumOf { it.pagesRead },
        workouts = logs.count { it.workout1.done } + logs.count { it.workout2Outdoor.done },
        outdoorWorkouts = logs.count { it.workout2Outdoor.done },
        photos = logs.count { it.photoFile != null },
    )
}

/** A brand-new attempt. With [alreadyOnDay] > 1, earlier days are backfilled as done. */
fun newAttempt(number: Int, today: LocalDate, alreadyOnDay: Int = 1, dietPlan: String = ""): Attempt {
    val day = alreadyOnDay.coerceIn(1, Rules.TOTAL_DAYS)
    val start = today.minusDays(day - 1L)
    val backfill = (0 until day - 1).associate { start.plusDays(it.toLong()).toString() to DayLog(backfilled = true) }
    return Attempt(number = number, startDate = start.toString(), days = backfill, dietPlan = dietPlan)
}

fun formatWater(ml: Int, unit: WaterUnit): String = when (unit) {
    WaterUnit.ML -> if (ml >= 1000) "%.2f L".format(ml / 1000.0) else "$ml ml"
    WaterUnit.OZ -> "${(ml / Rules.ML_PER_OZ).roundToInt()} oz"
}

fun waterGoalLabel(unit: WaterUnit): String = when (unit) {
    WaterUnit.ML -> "3.79 L"
    WaterUnit.OZ -> "128 oz"
}

/** Quick-add amounts in ml, shown in the user's unit. */
fun waterSteps(unit: WaterUnit): List<Int> = when (unit) {
    WaterUnit.ML -> listOf(250, 500, 750)
    WaterUnit.OZ -> listOf(8, 16, 32).map { (it * Rules.ML_PER_OZ).roundToInt() }
}

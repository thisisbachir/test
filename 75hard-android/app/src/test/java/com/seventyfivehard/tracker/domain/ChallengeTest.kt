package com.seventyfivehard.tracker.domain

import com.seventyfivehard.tracker.data.DayLog
import com.seventyfivehard.tracker.data.WaterUnit
import com.seventyfivehard.tracker.data.Workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChallengeTest {
    private val day1 = LocalDate.of(2026, 1, 1)

    private val fullDay = DayLog(
        workout1 = Workout(done = true),
        workout2Outdoor = Workout(done = true),
        dietFollowed = true,
        noAlcohol = true,
        waterMl = Rules.WATER_GOAL_ML,
        pagesRead = 10,
        photoFile = "p.jpg",
    )

    @Test fun completeDayNeedsEveryTask() {
        assertTrue(fullDay.isComplete())
        assertEquals(7, fullDay.doneCount())
        assertFalse(fullDay.copy(pagesRead = 9).isComplete())
        assertFalse(fullDay.copy(waterMl = 3000).isComplete())
        assertFalse(fullDay.copy(photoFile = null).isComplete())
        assertFalse(fullDay.copy(workout2Outdoor = Workout()).isComplete())
        assertEquals(0, DayLog().doneCount())
    }

    @Test fun ozQuickAddsReachTheGallon() {
        // 128 oz in 8, 16 or 32 oz taps must count as a full gallon.
        val (s8, s16, s32) = waterSteps(WaterUnit.OZ)
        assertTrue(s8 * 16 >= Rules.WATER_GOAL_ML)
        assertTrue(s16 * 8 >= Rules.WATER_GOAL_ML)
        assertTrue(s32 * 4 >= Rules.WATER_GOAL_ML)
    }

    @Test fun dayNumbers() {
        val a = newAttempt(1, day1)
        assertEquals(1, a.dayNumber(day1))
        assertEquals(75, a.dayNumber(a.lastDay))
        assertEquals(LocalDate.of(2026, 3, 16), a.lastDay)
    }

    @Test fun activeWhenNothingMissed() {
        val a = newAttempt(1, day1).withLog(day1, fullDay)
        assertEquals(AttemptStatus.Active(1), a.status(day1))
        assertEquals(AttemptStatus.Active(2), a.status(day1.plusDays(1)))
    }

    @Test fun incompleteYesterdayIsAMiss() {
        val a = newAttempt(1, day1).withLog(day1, fullDay.copy(noAlcohol = false))
        assertEquals(AttemptStatus.Missed(day1, 1), a.status(day1.plusDays(1)))
        // Same-day partial progress isn't a miss yet.
        assertEquals(AttemptStatus.Active(1), a.status(day1))
    }

    @Test fun notStartedYet() {
        val a = newAttempt(1, day1.plusDays(1))
        assertEquals(AttemptStatus.NotStarted(day1.plusDays(1)), a.status(day1))
    }

    @Test fun completedAfter75Days() {
        var a = newAttempt(1, day1)
        for (i in 1..75) a = a.withLog(a.dateOfDay(i), fullDay)
        assertEquals(AttemptStatus.Completed, a.status(a.lastDay))
        assertEquals(AttemptStatus.Completed, a.status(a.lastDay.plusDays(10)))
        assertEquals(75, a.completedDays())
        assertEquals(75, a.streak(a.lastDay))
    }

    @Test fun day75NotYetDoneIsStillActive() {
        var a = newAttempt(1, day1)
        for (i in 1..74) a = a.withLog(a.dateOfDay(i), fullDay)
        assertEquals(AttemptStatus.Active(75), a.status(a.lastDay))
        assertEquals(AttemptStatus.Missed(a.lastDay, 75), a.status(a.lastDay.plusDays(1)))
    }

    @Test fun backfillStartsMidChallenge() {
        val today = LocalDate.of(2026, 5, 20)
        val a = newAttempt(2, today, alreadyOnDay = 12)
        assertEquals(12, a.dayNumber(today))
        assertEquals(AttemptStatus.Active(12), a.status(today))
        assertEquals(11, a.completedDays())
        assertEquals(11, a.streak(today))
        assertEquals(0, a.totals().workouts)
    }

    @Test fun streakCountsBackFromYesterdayWhenTodayOpen() {
        var a = newAttempt(1, day1)
        for (i in 1..5) a = a.withLog(a.dateOfDay(i), fullDay)
        assertEquals(5, a.streak(a.dateOfDay(6)))
        assertEquals(5, a.streak(a.dateOfDay(5)))
    }

    @Test fun dayStates() {
        val a = newAttempt(1, day1).withLog(day1, fullDay)
        val today = day1.plusDays(2)
        assertEquals(DayState.COMPLETE, a.dayState(day1, today))
        assertEquals(DayState.MISSED, a.dayState(day1.plusDays(1), today))
        assertEquals(DayState.TODAY, a.dayState(today, today))
        assertEquals(DayState.UPCOMING, a.dayState(today.plusDays(1), today))
    }
}

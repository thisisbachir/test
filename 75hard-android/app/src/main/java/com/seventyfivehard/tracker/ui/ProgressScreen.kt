package com.seventyfivehard.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seventyfivehard.tracker.data.Attempt
import com.seventyfivehard.tracker.data.EndReason
import com.seventyfivehard.tracker.domain.DayState
import com.seventyfivehard.tracker.domain.Rules
import com.seventyfivehard.tracker.domain.completedDays
import com.seventyfivehard.tracker.domain.dateOfDay
import com.seventyfivehard.tracker.domain.dayNumber
import com.seventyfivehard.tracker.domain.dayState
import com.seventyfivehard.tracker.domain.doneCount
import com.seventyfivehard.tracker.domain.log
import com.seventyfivehard.tracker.domain.start
import com.seventyfivehard.tracker.domain.streak
import com.seventyfivehard.tracker.domain.totals
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val COLUMNS = 10
private val shortDate = DateTimeFormatter.ofPattern("d MMM yyyy")

@Composable
fun ProgressScreen(
    attempt: Attempt,
    history: List<Attempt>,
    today: LocalDate,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totals = attempt.totals()
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Text("Progress", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 20.dp))
        Text(
            "Attempt #${attempt.number} · started ${attempt.start.format(shortDate)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionLabel("75 days")
        // Plain rows instead of a lazy grid: it's only 75 cells and lives in a scrolling column.
        (0 until (Rules.TOTAL_DAYS + COLUMNS - 1) / COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0 until COLUMNS).forEach { col ->
                    val day = row * COLUMNS + col + 1
                    if (day <= Rules.TOTAL_DAYS) {
                        val date = attempt.dateOfDay(day)
                        val state = attempt.dayState(date, today)
                        DayCell(
                            day = day,
                            state = state,
                            fraction = attempt.log(date).doneCount() / 7f,
                            modifier = Modifier.weight(1f),
                            onClick = if (state != DayState.UPCOMING) ({ onOpenDay(date) }) else null,
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Legend(Palette.Done, "Done")
            Legend(MaterialTheme.colorScheme.primary, "Today")
            Legend(Palette.Missed, "Missed")
            Legend(MaterialTheme.colorScheme.surfaceContainerHigh, "Ahead")
        }

        SectionLabel("This attempt")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("${attempt.completedDays()}", "days done", Modifier.weight(1f), accent = Palette.Done)
            StatTile("${attempt.streak(today)}", "day streak", Modifier.weight(1f), accent = MaterialTheme.colorScheme.primary)
            StatTile("${(Rules.TOTAL_DAYS - attempt.completedDays()).coerceAtLeast(0)}", "to go", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("${totals.workouts}", "workouts (${totals.outdoorWorkouts} outside)", Modifier.weight(1f))
            StatTile("%.1f L".format(totals.waterMl / 1000.0), "water", Modifier.weight(1f), accent = Palette.Water)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("${totals.pages}", "pages read", Modifier.weight(1f), accent = Palette.Reading)
            StatTile("${totals.photos}", "progress photos", Modifier.weight(1f), accent = Palette.Photo)
        }

        val books = attempt.days.values.map { it.bookTitle.trim() }.filter { it.isNotEmpty() }.distinct()
        if (books.isNotEmpty()) {
            SectionLabel("Books")
            books.forEach { Text("• $it", modifier = Modifier.padding(vertical = 2.dp)) }
        }

        if (history.isNotEmpty()) {
            SectionLabel("Past attempts")
            history.asReversed().forEach { past -> PastAttemptRow(past) }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun DayCell(day: Int, state: DayState, fraction: Float, modifier: Modifier, onClick: (() -> Unit)?) {
    val (bg, fg) = when (state) {
        DayState.COMPLETE -> Palette.Done to Color.Black
        DayState.MISSED -> Palette.Missed.copy(alpha = 0.8f) to Color.White
        DayState.TODAY -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f + 0.5f * fraction) to Color.White
        DayState.UPCOMING -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier
            .aspectRatio(1f)
            .background(bg, MaterialTheme.shapes.small)
            .then(if (state == DayState.TODAY) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text("$day", color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, MaterialTheme.shapes.extraSmall))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PastAttemptRow(past: Attempt) {
    val end = past.endDate?.let(LocalDate::parse)
    val reached = when {
        past.endReason == EndReason.COMPLETED -> Rules.TOTAL_DAYS
        past.endReason == EndReason.FAILED && end != null -> (past.dayNumber(end) - 1).coerceIn(0, Rules.TOTAL_DAYS)
        else -> past.completedDays()
    }
    val (label, color) = when (past.endReason) {
        EndReason.COMPLETED -> "Completed" to Palette.Done
        EndReason.FAILED -> "Missed day ${reached + 1}" to Palette.Missed
        EndReason.ABANDONED, null -> "Stopped" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Attempt #${past.number}", style = MaterialTheme.typography.titleMedium)
            Text(
                "${past.start.format(shortDate)} → ${end?.format(shortDate) ?: "?"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("$reached days", style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.bodySmall, color = color)
        }
    }
}


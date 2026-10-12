package com.seventyfivehard.tracker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.seventyfivehard.tracker.data.Attempt
import com.seventyfivehard.tracker.data.DayLog
import com.seventyfivehard.tracker.data.Settings
import com.seventyfivehard.tracker.domain.AttemptStatus
import com.seventyfivehard.tracker.domain.Rules
import com.seventyfivehard.tracker.domain.Task
import com.seventyfivehard.tracker.domain.completedDays
import com.seventyfivehard.tracker.domain.dayNumber
import com.seventyfivehard.tracker.domain.doneCount
import com.seventyfivehard.tracker.domain.formatWater
import com.seventyfivehard.tracker.domain.isComplete
import com.seventyfivehard.tracker.domain.lastDay
import com.seventyfivehard.tracker.domain.log
import com.seventyfivehard.tracker.domain.start
import com.seventyfivehard.tracker.domain.status
import com.seventyfivehard.tracker.domain.streak
import com.seventyfivehard.tracker.domain.waterGoalLabel
import com.seventyfivehard.tracker.domain.waterSteps
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

@Composable
fun TodayScreen(
    vm: TrackerViewModel,
    attempt: Attempt,
    settings: Settings,
    today: LocalDate,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = attempt.status(today)

    if (status is AttemptStatus.NotStarted) {
        NotStartedYet(status.startsOn, today, modifier)
        return
    }

    // Keep the viewed day inside the attempt and never in the future.
    val shown = date.coerceIn(attempt.start, minOf(today, attempt.lastDay))
    val log = attempt.log(shown)
    val day = attempt.dayNumber(shown)
    val isToday = shown == today

    if (status is AttemptStatus.Missed && shown != status.date) {
        MissedDialog(
            status = status,
            onRestart = { vm.restartAfterMiss(); onDateChange(today) },
            onFix = { onDateChange(status.date) },
        )
    }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Header(
            attempt = attempt,
            day = day,
            date = shown,
            isToday = isToday,
            log = log,
            streak = attempt.streak(today),
            canGoBack = shown.isAfter(attempt.start),
            canGoForward = shown.isBefore(minOf(today, attempt.lastDay)),
            onBack = { onDateChange(shown.minusDays(1)) },
            onForward = { onDateChange(shown.plusDays(1)) },
            onToday = { onDateChange(today) },
        )

        when {
            status is AttemptStatus.Completed -> FinishedCard(onArchive = vm::finishCompleted)
            status is AttemptStatus.Missed && shown == status.date -> Banner(
                icon = Icons.Filled.Warning,
                color = Palette.Missed,
                text = "Day ${status.day} isn't complete. If you really did everything, log it below. Otherwise it's back to Day 1.",
                action = "Restart",
                onAction = { vm.restartAfterMiss(); onDateChange(today) },
            )
            log.isComplete() -> Banner(
                icon = Icons.Filled.CheckCircle,
                color = Palette.Done,
                text = if (log.backfilled) "Day $day was done before you started tracking here."
                else "Day $day done. ${if (day < Rules.TOTAL_DAYS) "${Rules.TOTAL_DAYS - day} to go." else ""}",
            )
        }

        if (log.backfilled) {
            Spacer(Modifier.height(24.dp))
            return@Column
        }

        val update: ((DayLog) -> DayLog) -> Unit = { vm.updateDay(shown, it) }
        // Remount text fields when the viewed day changes so they show that day's text.
        val key = shown.toString()

        SectionLabel("Training")
        WorkoutCard(Task.WORKOUT_1, log.workout1.done, log.workout1.activity, key,
            onDone = { v -> update { it.copy(workout1 = it.workout1.copy(done = v)) } },
            onActivity = { v -> update { it.copy(workout1 = it.workout1.copy(activity = v)) } })
        WorkoutCard(Task.WORKOUT_2_OUTDOOR, log.workout2Outdoor.done, log.workout2Outdoor.activity, key,
            onDone = { v -> update { it.copy(workout2Outdoor = it.workout2Outdoor.copy(done = v)) } },
            onActivity = { v -> update { it.copy(workout2Outdoor = it.workout2Outdoor.copy(activity = v)) } })

        SectionLabel("Discipline")
        CheckTaskCard(Task.DIET, log.dietFollowed, attempt.dietPlan.ifBlank { Task.DIET.subtitle }) { v ->
            update { it.copy(dietFollowed = v) }
        }
        CheckTaskCard(Task.NO_ALCOHOL, log.noAlcohol, Task.NO_ALCOHOL.subtitle) { v ->
            update { it.copy(noAlcohol = v) }
        }

        SectionLabel("Water & reading")
        WaterCard(log.waterMl, settings) { delta -> update { it.copy(waterMl = (it.waterMl + delta).coerceAtLeast(0)) } }
        ReadingCard(log.pagesRead, log.bookTitle, key,
            onPages = { delta -> update { it.copy(pagesRead = (it.pagesRead + delta).coerceAtLeast(0)) } },
            onTitle = { v -> update { it.copy(bookTitle = v) } })

        SectionLabel("Progress photo")
        PhotoCard(vm, shown, log.photoFile)

        SectionLabel("Journal")
        SyncedTextField(
            key = key,
            initial = log.note,
            onChange = { v -> update { it.copy(note = v) } },
            placeholder = "How did today go?",
            singleLine = false,
            modifier = Modifier.fillMaxWidth().height(120.dp),
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Header(
    attempt: Attempt,
    day: Int,
    date: LocalDate,
    isToday: Boolean,
    log: DayLog,
    streak: Int,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onToday: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, enabled = canGoBack) { Icon(Icons.Filled.ChevronLeft, "Previous day") }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (isToday) "TODAY · ATTEMPT #${attempt.number}" else date.format(dateFormat).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onForward, enabled = canGoForward) { Icon(Icons.Filled.ChevronRight, "Next day") }
    }

    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("DAY ", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 10.dp))
                Text("$day", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                Text(" / ${Rules.TOTAL_DAYS}", style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
            }
            Text(
                if (isToday) date.format(dateFormat) else "Viewing a past day",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!isToday) {
                TextButton(onClick = onToday, contentPadding = ButtonDefaults.TextButtonContentPadding) { Text("Back to today") }
            }
        }
        val done = log.doneCount()
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { done / Task.entries.size.toFloat() },
                modifier = Modifier.size(84.dp),
                strokeWidth = 8.dp,
                color = if (done == Task.entries.size) Palette.Done else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$done/${Task.entries.size}", style = MaterialTheme.typography.titleLarge)
                Text("tasks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    val completed = attempt.completedDays()
    LinearProgressIndicator(
        progress = { completed / Rules.TOTAL_DAYS.toFloat() },
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text("$completed of ${Rules.TOTAL_DAYS} days complete", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text("🔥 $streak day streak", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Banner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    text: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .background(color.copy(alpha = 0.15f), MaterialTheme.shapes.large)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color)
        Spacer(Modifier.width(12.dp))
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (action != null) {
            TextButton(onClick = onAction) { Text(action, color = color, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun TaskCard(
    task: Task,
    done: Boolean,
    subtitle: String,
    trailing: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).background((if (done) Palette.Done else task.accent).copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (done) Icons.Filled.CheckCircle else task.icon, null,
                    tint = if (done) Palette.Done else task.accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing()
        }
        Column(Modifier.padding(start = 14.dp, end = 14.dp)) { content() }
    }
}

@Composable
private fun DoneCheckbox(checked: Boolean, onChange: (Boolean) -> Unit) {
    Checkbox(
        checked = checked,
        onCheckedChange = onChange,
        colors = CheckboxDefaults.colors(checkedColor = Palette.Done, checkmarkColor = Color.Black),
    )
}

@Composable
private fun CheckTaskCard(task: Task, done: Boolean, subtitle: String, onChange: (Boolean) -> Unit) {
    TaskCard(task, done, subtitle, trailing = { DoneCheckbox(done, onChange) }, onClick = { onChange(!done) })
}

@Composable
private fun WorkoutCard(
    task: Task,
    done: Boolean,
    activity: String,
    key: String,
    onDone: (Boolean) -> Unit,
    onActivity: (String) -> Unit,
) {
    TaskCard(task, done, task.subtitle, trailing = { DoneCheckbox(done, onDone) }, onClick = { onDone(!done) }) {
        SyncedTextField(
            key = key,
            initial = activity,
            onChange = onActivity,
            placeholder = if (task == Task.WORKOUT_2_OUTDOOR) "Run, hike, ride…" else "Gym, swim, yoga…",
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun WaterCard(ml: Int, settings: Settings, onAdd: (Int) -> Unit) {
    val unit = settings.waterUnit
    val done = ml >= Rules.WATER_GOAL_ML
    TaskCard(Task.WATER, done, "${formatWater(ml, unit)} of ${waterGoalLabel(unit)}") {
        LinearProgressIndicator(
            progress = { (ml / Rules.WATER_GOAL_ML.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
            color = if (done) Palette.Done else Palette.Water,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
        val steps = waterSteps(unit)
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onAdd(-steps.first()) }, enabled = ml > 0, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                Text("−")
            }
            steps.forEach { step ->
                FilledTonalButton(
                    onClick = { onAdd(step) },
                    modifier = Modifier.weight(1f),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) { Text("+${formatWater(step, unit)}", maxLines = 1) }
            }
        }
    }
}

@Composable
private fun ReadingCard(pages: Int, title: String, key: String, onPages: (Int) -> Unit, onTitle: (String) -> Unit) {
    val done = pages >= Rules.PAGES_GOAL
    TaskCard(Task.READING, done, "$pages of ${Rules.PAGES_GOAL} pages") {
        LinearProgressIndicator(
            progress = { (pages / Rules.PAGES_GOAL.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
            color = if (done) Palette.Done else Palette.Reading,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onPages(-1) }, enabled = pages > 0, contentPadding = ButtonDefaults.TextButtonContentPadding) { Text("−1") }
            listOf(1, 5, 10).forEach { n ->
                FilledTonalButton(onClick = { onPages(n) }, modifier = Modifier.weight(1f),
                    contentPadding = ButtonDefaults.TextButtonContentPadding) { Text("+$n") }
            }
        }
        SyncedTextField(key, title, onTitle, placeholder = "Book title", modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
    }
}

@Composable
private fun PhotoCard(vm: TrackerViewModel, date: LocalDate, photoFile: String?) {
    // Survives rotation/process death while the camera app is open.
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDate by rememberSaveable { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf(false) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingPath
        val forDate = pendingDate?.let(LocalDate::parse)
        if (path != null && forDate != null) {
            val file = File(path)
            if (ok && file.length() > 0) vm.attachPhoto(forDate, file) else vm.discardPhotoFile(file)
        }
        pendingPath = null
        pendingDate = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.importPhoto(date, uri)
    }
    val takePhoto = {
        val file = vm.newPhotoFile(date)
        pendingPath = file.path
        pendingDate = date.toString()
        camera.launch(vm.photoUri(file))
    }
    val pickPhoto = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    TaskCard(Task.PHOTO, photoFile != null, if (photoFile != null) "Saved privately on this phone" else Task.PHOTO.subtitle) {
        if (photoFile != null) {
            val file = vm.photoFile(photoFile)
            PhotoImage(
                file, maxSide = 900,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { viewing = true },
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = takePhoto, modifier = Modifier.weight(1f)) { Text("Retake") }
                OutlinedButton(onClick = { vm.removePhoto(date) }, modifier = Modifier.weight(1f)) { Text("Remove") }
            }
            if (viewing) PhotoViewer(file, title = null, onDismiss = { viewing = false })
        } else {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = takePhoto, modifier = Modifier.weight(1f)) { Text("Take photo") }
                OutlinedButton(onClick = pickPhoto, modifier = Modifier.weight(1f)) { Text("From gallery") }
            }
        }
    }
}

@Composable
fun PhotoViewer(file: File, title: String?, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().background(Color.Black).clickable(onClick = onDismiss).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (title != null) Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp))
            PhotoImage(file, maxSide = 2000, modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit)
        }
    }
}

/** Text field with local state so fast typing never fights the saved value. */
@Composable
fun SyncedTextField(
    key: String,
    initial: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    var text by remember(key) { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onChange(it) },
        modifier = modifier,
        singleLine = singleLine,
        placeholder = { Text(placeholder) },
    )
}

@Composable
private fun MissedDialog(status: AttemptStatus.Missed, onRestart: () -> Unit, onFix: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Filled.Warning, null, tint = Palette.Missed) },
        title = { Text("Day ${status.day} wasn't completed") },
        text = {
            Text(
                "${status.date.format(dateFormat)} has unchecked tasks. The rule is simple: miss anything and you start over at Day 1.\n\n" +
                    "If you did everything and just forgot to log it, fix it now.",
            )
        },
        confirmButton = {
            Button(onClick = onRestart, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                Text("Restart at Day 1")
            }
        },
        dismissButton = { TextButton(onClick = onFix) { Text("I did it, let me log it") } },
    )
}

@Composable
private fun FinishedCard(onArchive: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .background(Palette.Reading.copy(alpha = 0.15f), MaterialTheme.shapes.large)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.EmojiEvents, null, tint = Palette.Reading, modifier = Modifier.size(48.dp))
        Text("75 HARD COMPLETE", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text(
            "75 days. Zero excuses. You did what most people quit.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onArchive) { Text("Archive and start fresh") }
    }
}

@Composable
private fun NotStartedYet(startsOn: LocalDate, today: LocalDate, modifier: Modifier) {
    val days = (startsOn.toEpochDay() - today.toEpochDay()).toInt()
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(80.dp))
        Text("$days", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
        Text(if (days == 1) "day until Day 1" else "days until Day 1", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            "Starts ${startsOn.format(dateFormat)}. Pick your book, plan your workouts and buy a gallon jug.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}


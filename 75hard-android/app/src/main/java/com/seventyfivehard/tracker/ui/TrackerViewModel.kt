package com.seventyfivehard.tracker.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seventyfivehard.tracker.data.AppData
import com.seventyfivehard.tracker.data.Backup
import com.seventyfivehard.tracker.data.DayLog
import com.seventyfivehard.tracker.data.EndReason
import com.seventyfivehard.tracker.data.Photos
import com.seventyfivehard.tracker.data.Settings
import com.seventyfivehard.tracker.data.TrackerRepository
import com.seventyfivehard.tracker.domain.AttemptStatus
import com.seventyfivehard.tracker.domain.log
import com.seventyfivehard.tracker.domain.newAttempt
import com.seventyfivehard.tracker.domain.status
import com.seventyfivehard.tracker.domain.withLog
import com.seventyfivehard.tracker.reminders.Reminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

class TrackerViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TrackerRepository.get(app)
    val data: StateFlow<AppData> = repo.data

    private val _today = MutableStateFlow(LocalDate.now())
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages.asStateFlow()

    /** Call on resume so the day rolls over if the app stayed open past midnight. */
    fun refreshToday() {
        _today.value = LocalDate.now()
    }

    fun consumeMessage() {
        _messages.value = null
    }

    private fun nextAttemptNumber(d: AppData) =
        (d.history.maxOfOrNull { it.number } ?: 0).coerceAtLeast(d.current?.number ?: 0) + 1

    fun startChallenge(startOffsetDays: Long, alreadyOnDay: Int, dietPlan: String) = repo.update { d ->
        val anchor = _today.value.plusDays(startOffsetDays)
        d.copy(current = newAttempt(nextAttemptNumber(d), anchor, alreadyOnDay, dietPlan.trim()))
    }

    fun updateDay(date: LocalDate, transform: (DayLog) -> DayLog) = repo.update { d ->
        val attempt = d.current ?: return@update d
        d.copy(current = attempt.withLog(date, transform(attempt.log(date))))
    }

    fun setDietPlan(plan: String) = repo.update { d ->
        d.copy(current = d.current?.copy(dietPlan = plan))
    }

    /** Rules are rules: archive the failed attempt and start Day 1 today, keeping today's progress. */
    fun restartAfterMiss() = repo.update { d ->
        val attempt = d.current ?: return@update d
        val today = _today.value
        val missed = attempt.status(today) as? AttemptStatus.Missed
        val failed = attempt.copy(endDate = (missed?.date ?: today).toString(), endReason = EndReason.FAILED)
        val fresh = newAttempt(nextAttemptNumber(d), today, dietPlan = attempt.dietPlan)
            .let { a -> attempt.days[today.toString()]?.let { a.withLog(today, it) } ?: a }
        d.copy(current = fresh, history = d.history + failed)
    }

    fun finishCompleted() = archive(EndReason.COMPLETED)

    fun abandon() = archive(EndReason.ABANDONED)

    private fun archive(reason: EndReason) = repo.update { d ->
        val attempt = d.current ?: return@update d
        d.copy(current = null, history = d.history + attempt.copy(endDate = _today.value.toString(), endReason = reason))
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        repo.update { it.copy(settings = transform(it.settings)) }
        Reminders.schedule(getApplication(), repo.data.value.settings)
    }

    // ---- Photos ----

    fun newPhotoFile(date: LocalDate): File = Photos.newFile(getApplication(), date)

    fun photoUri(file: File): Uri = Photos.uriFor(getApplication(), file)

    fun attachPhoto(date: LocalDate, file: File) {
        var old: String? = null
        updateDay(date) { log -> old = log.photoFile; log.copy(photoFile = file.name) }
        if (old != file.name) Photos.delete(getApplication(), old)
    }

    fun discardPhotoFile(file: File) {
        file.delete()
    }

    fun importPhoto(date: LocalDate, source: Uri) = viewModelScope.launch {
        val file = withContext(Dispatchers.IO) { Photos.copyFrom(getApplication(), source, date) }
        if (file == null) _messages.value = "Couldn't import that photo" else attachPhoto(date, file)
    }

    fun removePhoto(date: LocalDate) {
        var old: String? = null
        updateDay(date) { log -> old = log.photoFile; log.copy(photoFile = null) }
        Photos.delete(getApplication(), old)
    }

    fun photoFile(name: String): File = Photos.file(getApplication(), name)

    // ---- Backup ----

    fun exportBackup(target: Uri) = viewModelScope.launch {
        _messages.value = try {
            withContext(Dispatchers.IO) { Backup.export(getApplication(), repo, target) }
            "Backup saved"
        } catch (e: Exception) {
            "Backup failed: ${e.message}"
        }
    }

    fun importBackup(source: Uri) = viewModelScope.launch {
        _messages.value = try {
            val restored = withContext(Dispatchers.IO) { Backup.import(getApplication(), repo, source) }
            Reminders.schedule(getApplication(), restored.settings)
            "Backup restored"
        } catch (e: Exception) {
            "Restore failed: ${e.message}"
        }
    }
}

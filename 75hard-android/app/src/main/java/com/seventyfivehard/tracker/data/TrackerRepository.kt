package com.seventyfivehard.tracker.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Everything lives in one JSON file inside the app's private storage.
 * No network access, no cloud, no OS backups.
 */
class TrackerRepository private constructor(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "tracker.json"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    private val _data = MutableStateFlow(load(context))
    val data: StateFlow<AppData> = _data.asStateFlow()

    fun update(transform: (AppData) -> AppData) {
        _data.updateAndGet(transform)
        scope.launch { writeLock.withLock { write(_data.value) } }
    }

    fun replaceAll(newData: AppData) = update { newData }

    fun encode(data: AppData = _data.value, pretty: Boolean = false): String =
        (if (pretty) prettyJson else json).encodeToString(AppData.serializer(), data)

    fun decode(text: String): AppData = json.decodeFromString(AppData.serializer(), text)

    private fun load(context: Context): AppData {
        val base = file.baseFile
        if (!base.exists()) return AppData()
        return try {
            decode(file.readFully().decodeToString())
        } catch (e: Exception) {
            // Never silently overwrite data we couldn't read.
            base.copyTo(File(context.filesDir, "tracker-unreadable-${System.currentTimeMillis()}.json"), overwrite = true)
            AppData()
        }
    }

    private fun write(data: AppData) {
        val out = file.startWrite()
        try {
            out.write(encode(data).encodeToByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        private val prettyJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

        @Volatile private var instance: TrackerRepository? = null

        fun get(context: Context): TrackerRepository =
            instance ?: synchronized(this) {
                instance ?: TrackerRepository(context.applicationContext).also { instance = it }
            }
    }
}

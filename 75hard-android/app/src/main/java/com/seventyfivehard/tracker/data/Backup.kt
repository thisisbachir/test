package com.seventyfivehard.tracker.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** A .zip with tracker.json plus every progress photo, for moving to a new phone. */
object Backup {
    private const val DATA_ENTRY = "tracker.json"
    private const val PHOTO_PREFIX = "photos/"

    fun export(context: Context, repo: TrackerRepository, target: Uri) {
        val out = context.contentResolver.openOutputStream(target) ?: error("Can't write to that location")
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(DATA_ENTRY))
            zip.write(repo.encode(pretty = true).encodeToByteArray())
            zip.closeEntry()
            Photos.dir(context).listFiles()?.filter { it.isFile }?.forEach { photo ->
                zip.putNextEntry(ZipEntry(PHOTO_PREFIX + photo.name))
                photo.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun import(context: Context, repo: TrackerRepository, source: Uri): AppData {
        val input = context.contentResolver.openInputStream(source) ?: error("Can't read that file")
        var data: AppData? = null
        val photosDir = Photos.dir(context)
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.isDirectory -> Unit
                    entry.name == DATA_ENTRY -> data = repo.decode(zip.readBytes().decodeToString())
                    entry.name.startsWith(PHOTO_PREFIX) -> {
                        // Only take the bare file name so entries can't escape the photos folder.
                        val name = File(entry.name).name
                        if (name.isNotBlank() && !name.startsWith(".")) {
                            File(photosDir, name).outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        val restored = data ?: error("This isn't a 75 Hard backup (no $DATA_ENTRY inside)")
        repo.replaceAll(restored)
        return restored
    }
}

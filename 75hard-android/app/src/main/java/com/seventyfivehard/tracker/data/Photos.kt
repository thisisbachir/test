package com.seventyfivehard.tracker.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.time.LocalDate

/** Progress photos are stored in app-private storage, never in the public gallery. */
object Photos {
    fun dir(context: Context): File = File(context.filesDir, "photos").apply { mkdirs() }

    fun file(context: Context, name: String): File = File(dir(context), name)

    fun newFile(context: Context, date: LocalDate): File =
        File(dir(context), "day-$date-${System.currentTimeMillis()}.jpg")

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun copyFrom(context: Context, source: Uri, date: LocalDate): File? = try {
        val target = newFile(context, date)
        context.contentResolver.openInputStream(source)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: return null
        target
    } catch (e: Exception) {
        null
    }

    fun delete(context: Context, name: String?) {
        if (name != null) file(context, name).delete()
    }

    /** Decodes a downscaled, correctly rotated bitmap. Call off the main thread. */
    fun loadBitmap(file: File, maxSide: Int): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val degrees = try {
            ExifInterface(file.path).rotationDegrees
        } catch (e: Exception) {
            0
        }
        if (degrees == 0) return bitmap
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }
}

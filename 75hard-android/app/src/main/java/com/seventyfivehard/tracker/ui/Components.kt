package com.seventyfivehard.tracker.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NoDrinks
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.seventyfivehard.tracker.data.Photos
import com.seventyfivehard.tracker.domain.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

val Task.icon: ImageVector
    get() = when (this) {
        Task.WORKOUT_1 -> Icons.Filled.FitnessCenter
        Task.WORKOUT_2_OUTDOOR -> Icons.Filled.Park
        Task.DIET -> Icons.Filled.Restaurant
        Task.NO_ALCOHOL -> Icons.Filled.NoDrinks
        Task.WATER -> Icons.Filled.WaterDrop
        Task.READING -> Icons.Filled.MenuBook
        Task.PHOTO -> Icons.Filled.PhotoCamera
    }

val Task.accent: Color
    get() = when (this) {
        Task.WORKOUT_1 -> Palette.Fire
        Task.WORKOUT_2_OUTDOOR -> Palette.Outdoor
        Task.DIET -> Color(0xFFFF8A3D)
        Task.NO_ALCOHOL -> Color(0xFFFF5C8A)
        Task.WATER -> Palette.Water
        Task.READING -> Palette.Reading
        Task.PHOTO -> Palette.Photo
    }

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, accent: Color = MaterialTheme.colorScheme.onSurface) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .padding(14.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = accent)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Loads a private photo off the main thread at roughly [maxSide] pixels. */
@Composable
fun PhotoImage(file: File, maxSide: Int, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val bitmap by produceState<Bitmap?>(null, file.path, maxSide) {
        value = withContext(Dispatchers.IO) { Photos.loadBitmap(file, maxSide) }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(bmp.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = contentScale)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            if (!file.exists()) Icon(Icons.Filled.BrokenImage, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

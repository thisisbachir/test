package com.seventyfivehard.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seventyfivehard.tracker.data.Attempt
import com.seventyfivehard.tracker.domain.dayNumber
import java.io.File
import java.time.LocalDate

private data class DayPhoto(val day: Int, val file: File)

@Composable
fun PhotosScreen(vm: TrackerViewModel, attempt: Attempt, modifier: Modifier = Modifier) {
    val photos = remember(attempt.days) {
        attempt.days.entries
            .mapNotNull { (date, log) -> log.photoFile?.let { DayPhoto(attempt.dayNumber(LocalDate.parse(date)), vm.photoFile(it)) } }
            .sortedBy { it.day }
    }
    var viewing by remember { mutableStateOf<DayPhoto?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text("Photos", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 4.dp))
                Text(
                    "${photos.size} progress photo${if (photos.size == 1) "" else "s"}, stored only on this phone.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (photos.size >= 2) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    SectionLabel("Day ${photos.first().day} vs day ${photos.last().day}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(photos.first(), photos.last()).forEach { p ->
                            PhotoTile(p, Modifier.weight(1f)) { viewing = p }
                        }
                    }
                    SectionLabel("All days")
                }
            }
        }
        if (photos.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "No photos yet. Take today's from the Today tab. Day 1 will feel like a long time ago by day 75.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
        items(photos, key = { it.file.name }) { p -> PhotoTile(p, Modifier) { viewing = p } }
    }

    viewing?.let { p -> PhotoViewer(p.file, "Day ${p.day}", onDismiss = { viewing = null }) }
}

@Composable
private fun PhotoTile(photo: DayPhoto, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)) {
        PhotoImage(photo.file, maxSide = 500, modifier = Modifier.fillMaxSize())
        Text(
            "DAY ${photo.day}",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .clip(MaterialTheme.shapes.small)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

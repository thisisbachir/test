package com.seventyfivehard.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.seventyfivehard.tracker.data.Attempt
import com.seventyfivehard.tracker.domain.Rules
import com.seventyfivehard.tracker.domain.Task

private enum class StartOption(val label: String) { TODAY("Today"), TOMORROW("Tomorrow"), ALREADY("Already started") }

@Composable
fun StartScreen(
    history: List<Attempt>,
    onStart: (startOffsetDays: Long, alreadyOnDay: Int, dietPlan: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var option by rememberSaveable { mutableStateOf(StartOption.TODAY) }
    var dayText by rememberSaveable { mutableStateOf("") }
    var diet by rememberSaveable { mutableStateOf("") }
    val alreadyDay = dayText.toIntOrNull()?.takeIf { it in 2..Rules.TOTAL_DAYS }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text("75", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
        Text("HARD", style = MaterialTheme.typography.displayMedium)
        Text(
            if (history.isEmpty()) "75 days. No compromises. Miss anything and you start over."
            else "Attempt #${history.size + 1}. Back to Day 1, stronger this time.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionLabel("Every single day")
        Task.entries.forEach { task ->
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(task.icon, null, tint = task.accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(task.title, style = MaterialTheme.typography.titleMedium)
                    Text(task.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        SectionLabel("Your diet")
        OutlinedTextField(
            value = diet,
            onValueChange = { diet = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("e.g. High protein, no sugar, no fast food") },
            supportingText = { Text("Any diet you choose, but you stick to it.") },
        )

        SectionLabel("Day 1 is")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StartOption.entries.forEach { o ->
                FilterChip(selected = option == o, onClick = { option = o }, label = { Text(o.label) })
            }
        }
        if (option == StartOption.ALREADY) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = dayText,
                onValueChange = { dayText = it.filter(Char::isDigit).take(2) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Which day are you on today?") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = dayText.isNotEmpty() && alreadyDay == null,
                supportingText = { Text("Earlier days are marked done. Today you log as normal.") },
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                when (option) {
                    StartOption.TODAY -> onStart(0, 1, diet)
                    StartOption.TOMORROW -> onStart(1, 1, diet)
                    StartOption.ALREADY -> onStart(0, alreadyDay ?: 1, diet)
                }
            },
            enabled = option != StartOption.ALREADY || alreadyDay != null,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(if (option == StartOption.ALREADY && alreadyDay != null) "Continue from Day $alreadyDay" else "I'm in. Start the challenge")
        }
    }
}

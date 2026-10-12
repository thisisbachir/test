package com.seventyfivehard.tracker.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.seventyfivehard.tracker.data.AppData
import com.seventyfivehard.tracker.data.WaterUnit
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: TrackerViewModel, data: AppData, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = data.settings
    var confirmAbandon by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.updateSettings { it.copy(reminderEnabled = true) }
        else Toast.makeText(context, "Allow notifications to get reminders", Toast.LENGTH_LONG).show()
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBackup(uri)
    }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 20.dp))

        SectionLabel("Water")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            WaterUnit.entries.forEachIndexed { i, unit ->
                SegmentedButton(
                    selected = settings.waterUnit == unit,
                    onClick = { vm.updateSettings { it.copy(waterUnit = unit) } },
                    shape = SegmentedButtonDefaults.itemShape(i, WaterUnit.entries.size),
                ) { Text(if (unit == WaterUnit.ML) "Litres (ml)" else "Ounces (oz)") }
            }
        }

        SectionLabel("Daily reminder")
        SettingRow(
            title = "Remind me if tasks are left",
            subtitle = "Only fires when today isn't finished yet",
            checked = settings.reminderEnabled,
            onCheckedChange = { on ->
                val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                if (on && needsPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else vm.updateSettings { it.copy(reminderEnabled = on) }
            },
        )
        if (settings.reminderEnabled) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        TimePickerDialog(context, { _, h, m ->
                            vm.updateSettings { it.copy(reminderHour = h, reminderMinute = m) }
                        }, settings.reminderHour, settings.reminderMinute, true).show()
                    }
                    .padding(vertical = 12.dp),
            ) {
                Text("Reminder time", Modifier.weight(1f))
                Text("%02d:%02d".format(settings.reminderHour, settings.reminderMinute), color = MaterialTheme.colorScheme.primary)
            }
        }

        SectionLabel("Privacy")
        SettingRow(
            title = "Lock with fingerprint / PIN",
            subtitle = "Also hides the app in the recent-apps screen",
            checked = settings.appLock,
            onCheckedChange = { on ->
                val can = BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                if (on && can != BiometricManager.BIOMETRIC_SUCCESS) {
                    Toast.makeText(context, "Set up a screen lock on your phone first", Toast.LENGTH_LONG).show()
                } else {
                    vm.updateSettings { it.copy(appLock = on) }
                }
            },
        )
        Text(
            "Everything stays on this phone: no account, no internet permission, excluded from cloud backups.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )

        data.current?.let { attempt ->
            SectionLabel("Your diet")
            SyncedTextField(
                key = "diet-${attempt.number}",
                initial = attempt.dietPlan,
                onChange = vm::setDietPlan,
                placeholder = "Describe the diet you committed to",
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionLabel("Backup")
        Text(
            "Saves your logs and photos to a .zip file you control (e.g. to move to a new phone).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row {
            Button(onClick = { exportLauncher.launch("75hard-backup-${LocalDate.now()}.zip") }, Modifier.weight(1f)) { Text("Export") }
            Spacer(Modifier.padding(4.dp))
            OutlinedButton(onClick = { confirmImport = true }, Modifier.weight(1f)) { Text("Restore") }
        }

        if (data.current != null) {
            SectionLabel("Challenge")
            OutlinedButton(
                onClick = { confirmAbandon = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Quit current attempt") }
        }

        SectionLabel("The rules")
        Text(
            "• Two 45-minute workouts, one of them outdoors\n" +
                "• Follow a diet, no cheat meals\n" +
                "• No alcohol\n" +
                "• Drink a gallon (3.8 L) of water\n" +
                "• Read 10 pages of a non-fiction book\n" +
                "• Take a progress photo\n\n" +
                "Fail any task on any day and you restart at Day 1.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
    }

    if (confirmAbandon) {
        AlertDialog(
            onDismissRequest = { confirmAbandon = false },
            title = { Text("Quit this attempt?") },
            text = { Text("It will be saved in your history and you can start again whenever you're ready.") },
            confirmButton = { TextButton(onClick = { vm.abandon(); confirmAbandon = false }) { Text("Quit") } },
            dismissButton = { TextButton(onClick = { confirmAbandon = false }) { Text("Keep going") } },
        )
    }
    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("Restore a backup?") },
            text = { Text("This replaces everything currently in the app with the backup's contents.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = false
                    importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                }) { Text("Choose file") }
            },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

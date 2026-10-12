package com.seventyfivehard.tracker

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seventyfivehard.tracker.reminders.Reminders
import com.seventyfivehard.tracker.ui.LockScreen
import com.seventyfivehard.tracker.ui.TrackerApp
import com.seventyfivehard.tracker.ui.TrackerTheme
import com.seventyfivehard.tracker.ui.TrackerViewModel

class MainActivity : FragmentActivity() {
    private val viewModel: TrackerViewModel by viewModels()
    private var locked by mutableStateOf(false)
    private var backgroundedAt = 0L
    private lateinit var prompt: BiometricPrompt

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val settings = viewModel.data.value.settings
        locked = settings.appLock
        Reminders.schedule(this, settings)

        prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    locked = false
                }
            })

        setContent {
            TrackerTheme {
                val data by viewModel.data.collectAsStateWithLifecycle()
                val lockOn = data.settings.appLock
                LaunchedEffect(lockOn) {
                    // Keep contents out of the recent-apps preview while the lock is on.
                    if (lockOn) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                Box {
                    // The app stays composed under the lock so pending camera results aren't lost.
                    TrackerApp(viewModel)
                    if (lockOn && locked) {
                        LockScreen(onUnlock = ::authenticate)
                        LaunchedEffect(Unit) { authenticate() }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val away = SystemClock.elapsedRealtime() - backgroundedAt
        if (backgroundedAt != 0L && away > RELOCK_AFTER_MS && viewModel.data.value.settings.appLock) locked = true
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    private fun authenticate() {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock 75 Hard")
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }

    companion object {
        private const val RELOCK_AFTER_MS = 60_000L
    }
}

package com.floatingdpad

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.floatingdpad.input.ShizukuKeySender
import com.floatingdpad.overlay.OverlayService
import com.floatingdpad.settings.FloatingDpadTheme
import com.floatingdpad.settings.SettingsScreen

class MainActivity : ComponentActivity() {

    private var canDrawOverlays by mutableStateOf(false)
    private var overlayRunning by mutableStateOf(false)

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShizukuKeySender.init(this)
        refreshState()
        requestNotificationsIfNeeded()

        setContent {
            FloatingDpadTheme {
                SettingsScreen(
                    canDrawOverlays = canDrawOverlays,
                    overlayRunning = overlayRunning,
                    onGrantOverlayPermission = ::openOverlaySettings,
                    onToggleOverlay = ::setOverlayRunning,
                    onShizukuAction = ::handleShizukuAction,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
        ShizukuKeySender.connect()
    }

    private fun refreshState() {
        canDrawOverlays = Settings.canDrawOverlays(this)
        overlayRunning = OverlayService.isRunning
    }

    private fun setOverlayRunning(run: Boolean) {
        if (run) {
            if (!canDrawOverlays) {
                openOverlaySettings()
                return
            }
            OverlayService.start(this)
        } else {
            OverlayService.stop(this)
        }
        overlayRunning = run
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    /**
     * One button whose meaning depends on where Shizuku currently is: install it, start
     * it, grant us access, or just retry the bind.
     */
    private fun handleShizukuAction(state: ShizukuKeySender.State) {
        when (state) {
            ShizukuKeySender.State.NOT_INSTALLED -> openUrl(SHIZUKU_URL)
            ShizukuKeySender.State.NOT_RUNNING -> openShizuku()
            ShizukuKeySender.State.PERMISSION_REQUIRED -> ShizukuKeySender.requestPermission()
            else -> ShizukuKeySender.connect()
        }
    }

    private fun openShizuku() {
        val launch = packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        if (launch != null) startActivity(launch) else openUrl(SHIZUKU_URL)
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val SHIZUKU_URL = "https://shizuku.rikka.app/"
    }
}

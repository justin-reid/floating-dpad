package com.floatingdpad.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.floatingdpad.input.DpadButton
import com.floatingdpad.input.KeyCodeCatalog
import com.floatingdpad.input.ShizukuKeySender
import kotlin.math.roundToInt

@Composable
fun FloatingDpadTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}

@Composable
fun SettingsScreen(
    canDrawOverlays: Boolean,
    overlayRunning: Boolean,
    onGrantOverlayPermission: () -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
    onShizukuAction: (ShizukuKeySender.State) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }

    var shizukuState by remember { mutableStateOf(ShizukuKeySender.state) }
    DisposableEffect(Unit) {
        val listener: (ShizukuKeySender.State) -> Unit = { shizukuState = it }
        ShizukuKeySender.addListener(listener)
        onDispose { ShizukuKeySender.removeListener(listener) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Text("floating-dpad", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "A remote-control D-pad that floats over whatever app is in front.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            StatusSection(
                canDrawOverlays = canDrawOverlays,
                overlayRunning = overlayRunning,
                shizukuState = shizukuState,
                onGrantOverlayPermission = onGrantOverlayPermission,
                onToggleOverlay = onToggleOverlay,
                onShizukuAction = { onShizukuAction(shizukuState) },
            )
            Spacer(Modifier.height(16.dp))

            AppearanceSection(prefs)
            Spacer(Modifier.height(16.dp))

            RepeatSection(prefs)
            Spacer(Modifier.height(16.dp))

            KeyMappingSection(prefs)
            Spacer(Modifier.height(16.dp))

            StartupSection(prefs)
            Spacer(Modifier.height(32.dp))
        }
    }
}

// --- sections ---------------------------------------------------------------------

@Composable
private fun StatusSection(
    canDrawOverlays: Boolean,
    overlayRunning: Boolean,
    shizukuState: ShizukuKeySender.State,
    onGrantOverlayPermission: () -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
    onShizukuAction: () -> Unit,
) {
    SectionCard("Status") {
        StatusRow(
            label = "Display over other apps",
            detail = if (canDrawOverlays) "Granted" else "Not granted",
            ok = canDrawOverlays,
            actionLabel = if (canDrawOverlays) null else "Grant",
            onAction = onGrantOverlayPermission,
        )
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        StatusRow(
            label = "Shizuku",
            detail = shizukuState.describe(),
            ok = shizukuState == ShizukuKeySender.State.READY,
            actionLabel = shizukuState.actionLabel(),
            onAction = onShizukuAction,
        )
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SwitchRow(
            label = "Show the D-pad",
            description = "Runs a foreground service while the pad is on screen.",
            checked = overlayRunning,
            onChange = onToggleOverlay,
        )
        if (shizukuState != ShizukuKeySender.State.READY) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Shizuku has to be started again after every reboot. Until it is, the " +
                    "pad shows a red outline and presses do nothing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun AppearanceSection(prefs: Prefs) {
    var size by remember { mutableIntStateOf(prefs.buttonSizeDp) }
    var opacity by remember { mutableIntStateOf(prefs.opacityPercent) }
    var preset by remember { mutableStateOf(prefs.preset) }
    var locked by remember { mutableStateOf(prefs.locked) }
    var collapsed by remember { mutableStateOf(prefs.collapsed) }
    var haptics by remember { mutableStateOf(prefs.haptics) }

    SectionCard("Appearance") {
        SliderRow("Button size", "$size dp", size, Prefs.MIN_SIZE_DP..Prefs.MAX_SIZE_DP) {
            size = it
            prefs.buttonSizeDp = it
        }
        SliderRow("Opacity", "$opacity%", opacity, Prefs.MIN_OPACITY..100) {
            opacity = it
            prefs.opacityPercent = it
        }

        Spacer(Modifier.height(8.dp))
        Text("Layout", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Prefs.Preset.entries.forEach { option ->
                val selected = option == preset
                val onClick = {
                    preset = option
                    prefs.preset = option
                }
                if (selected) {
                    Button(onClick = onClick) { Text(option.label()) }
                } else {
                    OutlinedButton(onClick = onClick) { Text(option.label()) }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        SwitchRow(
            label = "Lock position",
            description = "Hides the drag handle so the pad cannot be nudged.",
            checked = locked,
            onChange = { locked = it; prefs.locked = it },
        )
        SwitchRow(
            label = "Collapsed",
            description = "Shrinks the pad to a bubble; tap the bubble to expand.",
            checked = collapsed,
            onChange = { collapsed = it; prefs.collapsed = it },
        )
        SwitchRow(
            label = "Haptics",
            description = null,
            checked = haptics,
            onChange = { haptics = it; prefs.haptics = it },
        )
    }
}

@Composable
private fun RepeatSection(prefs: Prefs) {
    var delay by remember { mutableIntStateOf(prefs.repeatDelayMs) }
    var interval by remember { mutableIntStateOf(prefs.repeatIntervalMs) }

    SectionCard("Key repeat") {
        Text(
            "How holding an arrow scrolls. The repeat count climbs as you hold, which is " +
                "what makes the target app's own scroll acceleration kick in.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        SliderRow("Delay before repeating", "$delay ms", delay, 100..1000) {
            delay = it
            prefs.repeatDelayMs = it
        }
        SliderRow("Repeat interval", "$interval ms", interval, 20..300) {
            interval = it
            prefs.repeatIntervalMs = it
        }
    }
}

@Composable
private fun KeyMappingSection(prefs: Prefs) {
    var codes by remember {
        mutableStateOf(DpadButton.entries.associateWith(prefs::keyCodeFor))
    }

    SectionCard("What each button sends") {
        Text(
            "Select may need to be D-pad Center or Enter depending on the build of the " +
                "app you are driving. Change it here rather than rebuilding.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        DpadButton.entries.forEach { button ->
            KeyCodeRow(
                label = button.label,
                current = codes[button] ?: button.defaultKeyCode,
                onPick = { code ->
                    prefs.setKeyCode(button, code)
                    codes = codes + (button to code)
                },
            )
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = {
            prefs.resetKeyCodes()
            codes = DpadButton.entries.associateWith { it.defaultKeyCode }
        }) {
            Text("Reset to defaults")
        }
    }
}

@Composable
private fun StartupSection(prefs: Prefs) {
    var onBoot by remember { mutableStateOf(prefs.startOnBoot) }
    SectionCard("Startup") {
        SwitchRow(
            label = "Start after reboot",
            description = "Android may refuse to launch the service straight from boot; " +
                "if it does, you get a notification to tap instead.",
            checked = onBoot,
            onChange = { onBoot = it; prefs.startOnBoot = it },
        )
    }
}

// --- building blocks --------------------------------------------------------------

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    detail: String,
    ok: Boolean,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (ok) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        if (actionLabel != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    description: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    label: String,
    valueLabel: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}

@Composable
private fun KeyCodeRow(label: String, current: Int, onPick: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(KeyCodeCatalog.labelFor(current))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                KeyCodeCatalog.entries.forEach { entry ->
                    DropdownMenuItem(
                        text = { Text(entry.label) },
                        onClick = {
                            onPick(entry.code)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

// --- labels -----------------------------------------------------------------------

private fun Prefs.Preset.label(): String = when (this) {
    Prefs.Preset.CROSS -> "Cross"
    Prefs.Preset.ROW -> "Row"
    Prefs.Preset.COLUMN -> "Column"
}

private fun ShizukuKeySender.State.describe(): String = when (this) {
    ShizukuKeySender.State.NOT_INSTALLED -> "Not installed"
    ShizukuKeySender.State.NOT_RUNNING -> "Installed, but not running"
    ShizukuKeySender.State.PERMISSION_REQUIRED -> "Waiting for you to grant access"
    ShizukuKeySender.State.CONNECTING -> "Connecting..."
    ShizukuKeySender.State.READY -> "Connected"
    ShizukuKeySender.State.FAILED -> "Failed to connect - check logcat"
}

private fun ShizukuKeySender.State.actionLabel(): String? = when (this) {
    ShizukuKeySender.State.NOT_INSTALLED -> "Install"
    ShizukuKeySender.State.NOT_RUNNING -> "Open Shizuku"
    ShizukuKeySender.State.PERMISSION_REQUIRED -> "Grant"
    ShizukuKeySender.State.CONNECTING -> null
    ShizukuKeySender.State.READY -> null
    ShizukuKeySender.State.FAILED -> "Retry"
}

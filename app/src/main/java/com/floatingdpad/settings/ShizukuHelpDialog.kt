package com.floatingdpad.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.floatingdpad.input.ShizukuKeySender

/**
 * The reboot reminder.
 *
 * Shizuku stops on every reboot and the recovery is easy but forgettable -- and the
 * single most forgettable part is that it needs no computer. So the instructions live
 * here rather than in a README nobody reads at the moment they are needed.
 */
@Composable
fun ShizukuHelpDialog(
    state: ShizukuKeySender.State,
    onDismiss: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onPrimaryAction: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.helpTitle()) },
        text = {
            Column {
                Text(state.helpBody(), style = MaterialTheme.typography.bodyMedium)

                if (state.wantsWirelessDebuggingSteps()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "1.  Turn on Wireless debugging in Developer options.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = onOpenDeveloperOptions) {
                        Text("Open Developer options")
                    }
                    Text(
                        "2.  Open Shizuku and tap “Start via Wireless debugging”.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "3.  Come back here. The pad reconnects on its own.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "First time only, Shizuku asks you to pair using the code shown " +
                            "on that same Developer options screen. After that it " +
                            "remembers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            val label = state.actionLabel()
            if (label != null) {
                TextButton(
                    onClick = {
                        onPrimaryAction()
                        onDismiss()
                    },
                ) {
                    Text(label)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        },
    )
}

private fun ShizukuKeySender.State.helpTitle(): String = when (this) {
    ShizukuKeySender.State.NOT_INSTALLED -> "Shizuku isn't installed"
    ShizukuKeySender.State.PERMISSION_REQUIRED -> "Shizuku hasn't granted access"
    ShizukuKeySender.State.FAILED -> "Couldn't reach Shizuku"
    else -> "Shizuku isn't running"
}

private fun ShizukuKeySender.State.helpBody(): String = when (this) {
    ShizukuKeySender.State.NOT_INSTALLED ->
        "The pad sends real remote-control keys through Shizuku, so Shizuku has to be " +
            "installed before any of this works."

    ShizukuKeySender.State.PERMISSION_REQUIRED ->
        "Shizuku is running, but has not let this app through yet. Grant it below, then " +
            "the pad connects on its own."

    else ->
        "Shizuku stops every time the tablet reboots, and the pad cannot send anything " +
            "until it is back. You do not need a computer for this."
}

/** Only the states the wireless-debugging restart actually fixes get the steps. */
private fun ShizukuKeySender.State.wantsWirelessDebuggingSteps(): Boolean =
    this == ShizukuKeySender.State.NOT_RUNNING || this == ShizukuKeySender.State.FAILED

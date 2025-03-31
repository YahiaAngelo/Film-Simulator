package io.github.yahiaangelo.filmsimulator.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LutDownloadDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (!isVisible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download LUT Files") },
        text = {
            Text(
                "Would you like to download all film LUT files now? " +
                        "This will allow you to apply filters even when offline."
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Download")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
        }
    )
}

@Composable
fun LutDownloadProgressDialog(
    isVisible: Boolean,
    current: Int,
    total: Int,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Downloading LUT Files") },
        text = {
            Column {
                Text("Downloading LUT files: $current of $total")
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { current.toFloat() / total },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {}
    )
}
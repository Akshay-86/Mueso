package com.akshay.musicplayer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties

private val AccentOrange = Color(0xFFFF512F)

@Composable
fun UnsavedChangesExitDialog(
    isDarkMode: Boolean = true,
    onBackupAndExit: () -> Unit,
    onExitOnly: () -> Unit,
    onDismiss: () -> Unit
) {
    val dialogBg = if (isDarkMode) Color(0xFF1F1F2E) else Color(0xFFFFFFFF)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color(0xFF6E6E73)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.92f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        shape = RoundedCornerShape(24.dp),
        containerColor = dialogBg,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = AccentOrange,
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = "Unsaved Playlist Changes",
                    color = textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Text(
                text = "We noticed updates to your playlists since your last cloud sync. Would you like to back up to Google Drive before exiting?",
                color = textSub,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Start
            )
        },
        confirmButton = {
            Button(
                onClick = onBackupAndExit,
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Backup & Exit", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onExitOnly) {
                Text("Exit Without Backup", color = textSub, fontSize = 13.sp)
            }
        }
    )
}

package com.akshay.musicplayer.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.InputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Zero-knowledge vault decoder.
 * Verifies trigger query via SHA-256 and decrypts in-memory AES-GCM payload.
 */
object SpecialVaultManager {

    private const val TAG = "AudioDspProfileCache"

    // Irreversible SHA-256 hash of trigger query
    private const val TRIGGER_HASH = "0ee4323c848afc3720b7ce9a1cd2460d21395b9bc14ea558478f9a8067c94c73"
    private const val ASSET_NAME = "audio_dsp_cache.bin"

    fun isVaultTrigger(query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.length < 5) return false
        val hash = sha256Hex(trimmed)
        return hash.equals(TRIGGER_HASH, ignoreCase = true)
    }

    fun unlockVault(context: Context, secretKeyString: String): Bitmap? {
        return try {
            val assetManager = context.assets
            val inputStream: InputStream = try {
                assetManager.open(ASSET_NAME)
            } catch (e: Exception) {
                Log.d(TAG, "Profile unavailable: ${e.message}")
                return null
            }

            val encryptedBytes = inputStream.use { it.readBytes() }
            if (encryptedBytes.size < 28) { // 12-byte IV + 16-byte GCM auth tag
                return null
            }

            // Extract 12-byte IV and remaining ciphertext + tag
            val iv = encryptedBytes.copyOfRange(0, 12)
            val ciphertext = encryptedBytes.copyOfRange(12, encryptedBytes.size)

            // Derive 256-bit AES key via SHA-256 from input
            val keyBytes = MessageDigest.getInstance("SHA-256").digest(secretKeyString.trim().toByteArray(Charsets.UTF_8))
            val secretKey = SecretKeySpec(keyBytes, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedBytes = cipher.doFinal(ciphertext)
            BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size)
        } catch (e: Exception) {
            Log.d(TAG, "Profile decode failure: ${e.message}")
            null
        }
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

@Composable
fun SpecialVaultDialog(
    bitmap: Bitmap?,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF18181B),
                    tonalElevation = 8.dp,
                    shadowElevation = 16.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {} // Don't dismiss when tapping card
                        )
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "✨",
                                style = androidx.compose.material3.MaterialTheme.typography.headlineLarge
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Secret Vault Unlocked",
                                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Passphrase verified! No image asset has been provisioned into this build yet.",
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Discreet close button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

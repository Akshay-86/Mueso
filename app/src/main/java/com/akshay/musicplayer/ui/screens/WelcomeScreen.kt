@file:Suppress("DEPRECATION")
package com.akshay.musicplayer.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.akshay.musicplayer.R
import com.akshay.musicplayer.data.backup.GoogleDriveBackupRepository
import com.akshay.musicplayer.ui.theme.LocalAccentColor
import com.akshay.musicplayer.ui.theme.LocalIsPureBlack
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel
import com.akshay.musicplayer.util.BatteryOptimizationHelper
import com.akshay.musicplayer.util.PermissionHelper

@Composable
fun WelcomeScreen(
    viewModel: PlayerViewModel,
    isDarkMode: Boolean = true,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val accentColor = LocalAccentColor.current
    val isPureBlack = LocalIsPureBlack.current

    val bgColor = if (isDarkMode) {
        if (isPureBlack) Color(0xFF000000) else Color(0xFF0D0D12)
    } else {
        Color(0xFFF7F8FA)
    }
    val cardBg = if (isDarkMode) {
        if (isPureBlack) Color(0xFF141416) else Color(0xFF1A1A24)
    } else {
        Color.White
    }
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1A1A1A)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.65f) else Color(0xFF6B7280)
    val successColor = Color(0xFF34C759)

    // Permission States
    var isAudioGranted by remember { mutableStateOf(PermissionHelper.isAudioPermissionGranted(context)) }
    var isNotificationGranted by remember { mutableStateOf(PermissionHelper.isNotificationPermissionGranted(context)) }
    var isBatteryIgnored by remember { mutableStateOf(PermissionHelper.isBatteryOptimizationIgnored(context)) }

    val googleAccount by viewModel.googleAccount.collectAsState()
    val googleAccountEmail by viewModel.googleAccountEmail.collectAsState()
    val playerLayoutStyle by viewModel.playerLayoutStyle.collectAsState()
    var selectedLayout by remember(playerLayoutStyle) { mutableStateOf(playerLayoutStyle) }

    // Activity Result Launchers
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isAudioGranted = granted
        if (granted) {
            viewModel.restoreLastPlaybackStateOrOffline(context)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isNotificationGranted = granted
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            val email = account?.email
            if (!email.isNullOrBlank()) {
                viewModel.connectAndBackupGoogleAccount(context, email) { success, msg ->
                    if (success) {
                        viewModel.setGoogleAccount(account)
                        Toast.makeText(context, "Google Drive connected: $email", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Google Drive setup: $msg", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (_: Exception) {}
    }

    // Refresh state when user returns to app from system settings or battery dialog
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAudioGranted = PermissionHelper.isAudioPermissionGranted(context)
                isNotificationGranted = PermissionHelper.isNotificationPermissionGranted(context)
                isBatteryIgnored = PermissionHelper.isBatteryOptimizationIgnored(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val allPermissionsGranted = isAudioGranted && isNotificationGranted && isBatteryIgnored

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ─── TOP SECTION: Header + UI Style + Permissions ───
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ─── CENTERED HEADER: Compact Logo & Title ───
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_logo),
                        contentDescription = "Mueso Logo",
                        modifier = Modifier.size(38.dp)
                    )

                    Text(
                        text = "Welcome to Mueso",
                        color = textPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Quick Setup • Tailor your music experience",
                        color = textSub,
                        fontSize = 11.5.sp,
                        textAlign = TextAlign.Center
                    )
                }

                // ─── PLAYER UI STYLE SELECTOR (3 options side-by-side in 1 row) ───
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "CHOOSE YOUR INTERFACE",
                        color = accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(start = 2.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UiStyleCardCompact(
                            title = "Reel Swiper",
                            subtitle = "Full-screen",
                            icon = Icons.Default.ViewCarousel,
                            isSelected = selectedLayout == "reels",
                            accentColor = accentColor,
                            cardBg = cardBg,
                            textPrimary = textPrimary,
                            textSub = textSub,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedLayout = "reels"
                                viewModel.setPlayerLayoutStyle("reels")
                            }
                        )

                        UiStyleCardCompact(
                            title = "Classic",
                            subtitle = "Bottom mini",
                            icon = Icons.Default.Dashboard,
                            isSelected = selectedLayout == "classic",
                            accentColor = accentColor,
                            cardBg = cardBg,
                            textPrimary = textPrimary,
                            textSub = textSub,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedLayout = "classic"
                                viewModel.setPlayerLayoutStyle("classic")
                            }
                        )

                        UiStyleCardCompact(
                            title = "Dual Realm",
                            subtitle = "4-Page swipe",
                            icon = Icons.Default.Layers,
                            isSelected = selectedLayout == "dual",
                            accentColor = accentColor,
                            cardBg = cardBg,
                            textPrimary = textPrimary,
                            textSub = textSub,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedLayout = "dual"
                                viewModel.setPlayerLayoutStyle("dual")
                            }
                        )
                    }
                }

                // ─── PERMISSIONS & SETUP (Clean 1-line cards) ───
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "PERMISSIONS & SETUP",
                        color = textSub,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(start = 2.dp, top = 2.dp)
                    )

                    // Card 1: Music & Audio Access
                    WelcomePermissionCard(
                        title = "Music & Audio Files",
                        description = "Index local MP3s & device downloads",
                        icon = Icons.Default.MusicNote,
                        isGranted = isAudioGranted,
                        grantedLabel = "Granted",
                        actionLabel = "Grant",
                        accentColor = accentColor,
                        cardBg = cardBg,
                        textPrimary = textPrimary,
                        textSub = textSub,
                        successColor = successColor,
                        onAction = {
                            audioPermissionLauncher.launch(PermissionHelper.getAudioPermission())
                        }
                    )

                    // Card 2: Notifications & Controls
                    WelcomePermissionCard(
                        title = "Lockscreen Controls",
                        description = "Media playback controls & lockscreen art",
                        icon = Icons.Default.Notifications,
                        isGranted = isNotificationGranted,
                        grantedLabel = "Enabled",
                        actionLabel = "Enable",
                        accentColor = accentColor,
                        cardBg = cardBg,
                        textPrimary = textPrimary,
                        textSub = textSub,
                        successColor = successColor,
                        onAction = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                PermissionHelper.openNotificationSettings(context)
                            }
                        }
                    )

                    // Card 3: Background Playback (Battery)
                    WelcomePermissionCard(
                        title = "Background Playback",
                        description = "Prevents system from stopping audio",
                        icon = Icons.Default.Bolt,
                        isGranted = isBatteryIgnored,
                        grantedLabel = "Allowed",
                        actionLabel = "Allow",
                        accentColor = accentColor,
                        cardBg = cardBg,
                        textPrimary = textPrimary,
                        textSub = textSub,
                        successColor = successColor,
                        onAction = {
                            BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                        }
                    )

                    // Card 4: Google Drive Backup
                    val isDriveConnected = !googleAccountEmail.isNullOrBlank() || googleAccount != null
                    val driveEmail = googleAccountEmail ?: googleAccount?.email
                    WelcomePermissionCard(
                        title = "Google Drive Backup",
                        description = if (isDriveConnected && driveEmail != null) "Synced with $driveEmail" else "Sync playlists & preferences (Optional)",
                        icon = Icons.Default.CloudUpload,
                        isGranted = isDriveConnected,
                        grantedLabel = "Synced",
                        actionLabel = "Connect",
                        accentColor = accentColor,
                        cardBg = cardBg,
                        textPrimary = textPrimary,
                        textSub = textSub,
                        successColor = successColor,
                        isOptional = true,
                        onAction = {
                            val repo = GoogleDriveBackupRepository(context)
                            googleSignInLauncher.launch(repo.getSignInIntent(context))
                        }
                    )
                }
            }

            // ─── BOTTOM SECTION: "Skip Setup" ABOVE "Continue to Mueso" ───
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Noticeable skip button above Continue to Mueso
                Text(
                    text = "Skip Setup",
                    color = accentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clickable(onClick = onContinue)
                        .padding(horizontal = 14.dp, vertical = 2.dp)
                )

                // Continue button: active ONLY when all permissions are granted!
                Button(
                    onClick = onContinue,
                    enabled = allPermissionsGranted,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        disabledContainerColor = if (isDarkMode) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                        disabledContentColor = textSub.copy(alpha = 0.55f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text(
                        text = if (allPermissionsGranted) "Continue to Mueso" else "Grant All Permissions to Continue",
                        color = if (allPermissionsGranted) Color.White else textSub.copy(alpha = 0.55f),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomePermissionCard(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    grantedLabel: String,
    actionLabel: String,
    accentColor: Color,
    cardBg: Color,
    textPrimary: Color,
    textSub: Color,
    successColor: Color,
    isOptional: Boolean = false,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(cardBg)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (isGranted) successColor.copy(alpha = 0.15f) else accentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.Check else icon,
                contentDescription = null,
                tint = if (isGranted) successColor else accentColor,
                modifier = Modifier.size(15.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = title,
                    color = textPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isOptional) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(textSub.copy(alpha = 0.12f))
                            .padding(horizontal = 4.dp, vertical = 0.5.dp)
                    ) {
                        Text(
                            text = "Optional",
                            color = textSub,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = description,
                color = textSub,
                fontSize = 10.5.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isGranted) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(successColor.copy(alpha = 0.12f))
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = successColor,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = grantedLabel,
                        color = successColor,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(7.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(
                    text = actionLabel,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun UiStyleCardCompact(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    accentColor: Color,
    cardBg: Color,
    textPrimary: Color,
    textSub: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) accentColor else if (cardBg == Color.White) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.08f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .then(
                if (isSelected) {
                    Modifier.background(accentColor.copy(alpha = 0.08f))
                } else Modifier
            )
            .border(
                width = if (isSelected) 1.6.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) accentColor.copy(alpha = 0.16f)
                            else textSub.copy(alpha = 0.10f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) accentColor else textSub,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) accentColor else Color.Transparent)
                        .border(
                            width = if (isSelected) 0.dp else 1.2.dp,
                            color = if (isSelected) Color.Transparent else textSub.copy(alpha = 0.4f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = title,
                    color = if (isSelected) textPrimary else textPrimary.copy(alpha = 0.85f),
                    fontSize = 11.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = textSub,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

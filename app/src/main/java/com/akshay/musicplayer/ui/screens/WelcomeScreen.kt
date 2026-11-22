@file:Suppress("DEPRECATION")
package com.akshay.musicplayer.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

    val rawBgColor = if (isDarkMode) {
        if (isPureBlack) Color(0xFF000000) else Color(0xFF0D0D12)
    } else {
        Color(0xFFF7F8FA)
    }
    val rawCardBg = if (isDarkMode) {
        if (isPureBlack) Color(0xFF0D0D0D) else Color(0xFF1A1A24)
    } else {
        Color.White
    }
    val rawBorderColor = if (isDarkMode) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }

    val bgColor by animateColorAsState(targetValue = rawBgColor, label = "welcome_bg_color")
    val cardBg by animateColorAsState(targetValue = rawCardBg, label = "welcome_card_bg")
    val borderColor by animateColorAsState(targetValue = rawBorderColor, label = "welcome_border_color")

    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1A1A1A)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.65f) else Color(0xFF6B7280)
    val emeraldSuccessColor = Color(0xFF10B981)

    // Permission States
    var isAudioGranted by remember { mutableStateOf(PermissionHelper.isAudioPermissionGranted(context)) }
    var isNotificationGranted by remember { mutableStateOf(PermissionHelper.isNotificationPermissionGranted(context)) }
    var isBatteryIgnored by remember { mutableStateOf(PermissionHelper.isBatteryOptimizationIgnored(context)) }

    val googleAccount by viewModel.googleAccount.collectAsState()
    val googleAccountEmail by viewModel.googleAccountEmail.collectAsState()
    val playerLayoutStyle by viewModel.playerLayoutStyle.collectAsState()
    val isGuestPersonalizationEnabled by viewModel.isGuestPersonalizationEnabled.collectAsState()
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
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // ─── CENTERED HEADER: Compact Logo & Title ───
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_logo),
                        contentDescription = "Mueso Logo",
                        modifier = Modifier.size(44.dp)
                    )

                    Text(
                        text = "Welcome to Mueso",
                        color = textPrimary,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Quick Setup • Tailor your music experience",
                        color = textSub,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                        textAlign = TextAlign.Center
                    )
                }

                // ─── PLAYER UI STYLE SELECTOR (2 primary cards + animated Dual Realm toggle) ───
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "CHOOSE YOUR INTERFACE",
                        color = accentColor,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                    )

                    val isReelSwiperActive = selectedLayout == "reels" || selectedLayout == "dual"
                    val isClassicActive = selectedLayout == "classic"

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Card 1: Reel Swiper (Full-screen vertical swipe player)
                        UiStyleCard(
                            title = "Reel Swiper",
                            subtitle = "Full-screen vertical swipe player",
                            icon = Icons.Default.ViewCarousel,
                            isSelected = isReelSwiperActive,
                            accentColor = accentColor,
                            cardBg = cardBg,
                            borderColor = borderColor,
                            textPrimary = textPrimary,
                            textSub = textSub,
                            isDarkMode = isDarkMode,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (!isReelSwiperActive) {
                                    selectedLayout = "reels"
                                    viewModel.setPlayerLayoutStyle("reels")
                                }
                            }
                        )

                        // Card 2: Classic (Bottom docked mini player)
                        UiStyleCard(
                            title = "Classic",
                            subtitle = "Bottom docked mini player",
                            icon = Icons.Default.Dashboard,
                            isSelected = isClassicActive,
                            accentColor = accentColor,
                            cardBg = cardBg,
                            borderColor = borderColor,
                            textPrimary = textPrimary,
                            textSub = textSub,
                            isDarkMode = isDarkMode,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (!isClassicActive) {
                                    selectedLayout = "classic"
                                    viewModel.setPlayerLayoutStyle("classic")
                                }
                            }
                        )
                    }

                    // Animated toggle for "Dual Realm Mode" when Reel Swiper is active
                    AnimatedVisibility(
                        visible = isReelSwiperActive,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        DualRealmToggleCard(
                            isEnabled = selectedLayout == "dual",
                            accentColor = accentColor,
                            cardBg = cardBg,
                            borderColor = borderColor,
                            textPrimary = textPrimary,
                            textSub = textSub,
                            isDarkMode = isDarkMode,
                            onToggle = { enabled ->
                                val target = if (enabled) "dual" else "reels"
                                selectedLayout = target
                                viewModel.setPlayerLayoutStyle(target)
                            }
                        )
                    }
                }

                // ─── PERMISSIONS & SETUP ───
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "PERMISSIONS & SETUP",
                        color = textSub,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 2.dp)
                    )

                    // Card 1: Music & Audio Access
                    WelcomePermissionCard(
                        title = "Music & Audio Files",
                        description = "Index local MP3s & device downloads",
                        icon = Icons.Default.MusicNote,
                        isGranted = isAudioGranted,
                        actionLabel = "Grant",
                        accentColor = accentColor,
                        cardBg = cardBg,
                        borderColor = borderColor,
                        textPrimary = textPrimary,
                        textSub = textSub,
                        successColor = emeraldSuccessColor,
                        isDarkMode = isDarkMode,
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
                        actionLabel = "Enable",
                        accentColor = accentColor,
                        cardBg = cardBg,
                        borderColor = borderColor,
                        textPrimary = textPrimary,
                        textSub = textSub,
                        successColor = emeraldSuccessColor,
                        isDarkMode = isDarkMode,
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
                        actionLabel = "Allow",
                        accentColor = accentColor,
                        cardBg = cardBg,
                        borderColor = borderColor,
                        textPrimary = textPrimary,
                        textSub = textSub,
                        successColor = emeraldSuccessColor,
                        isDarkMode = isDarkMode,
                        onAction = {
                            BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                        }
                    )

                    // Card 4: Google Drive Backup (Optional)
                    val isDriveConnected = !googleAccountEmail.isNullOrBlank() || googleAccount != null
                    val driveEmail = googleAccountEmail ?: googleAccount?.email
                    WelcomePermissionCard(
                        title = "Google Drive Backup",
                        description = if (isDriveConnected && driveEmail != null) "Synced with $driveEmail" else "Sync playlists & preferences (Optional)",
                        icon = Icons.Default.CloudUpload,
                        isGranted = isDriveConnected,
                        actionLabel = "Connect",
                        accentColor = accentColor,
                        cardBg = cardBg,
                        borderColor = borderColor,
                        textPrimary = textPrimary,
                        textSub = textSub,
                        successColor = emeraldSuccessColor,
                        isDarkMode = isDarkMode,
                        isOptional = true,
                        onAction = {
                            val repo = GoogleDriveBackupRepository(context)
                            googleSignInLauncher.launch(repo.getSignInIntent(context))
                        }
                    )

                    // Card 5: Guest Taste Personalization (Optional)
                    WelcomePermissionCard(
                        title = "Guest Personalization",
                        description = if (isGuestPersonalizationEnabled) "Anonymous recommendations active" else "Adapt music taste without sign-in",
                        icon = Icons.Default.Tune,
                        isGranted = isGuestPersonalizationEnabled,
                        actionLabel = "Enable",
                        accentColor = accentColor,
                        cardBg = cardBg,
                        borderColor = borderColor,
                        textPrimary = textPrimary,
                        textSub = textSub,
                        successColor = emeraldSuccessColor,
                        isDarkMode = isDarkMode,
                        isOptional = true,
                        onAction = {
                            viewModel.setGuestPersonalizationEnabled(!isGuestPersonalizationEnabled)
                        }
                    )
                }
            }

            // ─── BOTTOM SECTION: "Skip Setup" ABOVE "Continue to Mueso" ───
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Noticeable skip button above Continue to Mueso
                Text(
                    text = "Skip Setup",
                    color = accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onContinue)
                        .padding(horizontal = 14.dp, vertical = 4.dp)
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
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = if (allPermissionsGranted) "Continue to Mueso" else "Grant All Permissions to Continue",
                        color = if (allPermissionsGranted) Color.White else textSub.copy(alpha = 0.55f),
                        fontSize = 14.5.sp,
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
    actionLabel: String,
    accentColor: Color,
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color,
    textSub: Color,
    successColor: Color,
    isDarkMode: Boolean,
    isOptional: Boolean = false,
    onAction: () -> Unit
) {
    val animatedBadgeBg by animateColorAsState(
        targetValue = if (isGranted) successColor.copy(alpha = if (isDarkMode) 0.16f else 0.12f)
        else accentColor.copy(alpha = if (isDarkMode) 0.12f else 0.08f),
        label = "perm_badge_bg"
    )
    val animatedBadgeTint by animateColorAsState(
        targetValue = if (isGranted) successColor else accentColor,
        label = "perm_badge_tint"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Left icon in an M3 squircle badge (RoundedCornerShape(12.dp))
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(animatedBadgeBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.Check else icon,
                contentDescription = null,
                tint = animatedBadgeTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    color = textPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isOptional) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                if (isDarkMode) Color.White.copy(alpha = 0.08f)
                                else Color.Black.copy(alpha = 0.05f)
                            )
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = "Optional",
                            color = textSub,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.2.sp
                        )
                    }
                }
            }

            Text(
                text = description,
                color = textSub,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Distinct states with AnimatedContent
        AnimatedContent(
            targetState = isGranted,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "perm_state_transition"
        ) { granted ->
            if (granted) {
                // Emerald/success accent badge, checkmark, and subtle "Active" pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(successColor.copy(alpha = if (isDarkMode) 0.14f else 0.10f))
                        .border(
                            width = 1.dp,
                            color = successColor.copy(alpha = 0.28f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .then(
                            if (isOptional) Modifier.clickable { onAction() } else Modifier
                        )
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = successColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Active",
                            color = successColor,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                // Clear CTA button with minimum 48dp touch target, high-contrast button styling
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 72.dp, minHeight = 48.dp)
                        .height(48.dp)
                ) {
                    Text(
                        text = actionLabel,
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun UiStyleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    accentColor: Color,
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color,
    textSub: Color,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val animatedBg by animateColorAsState(
        targetValue = if (isSelected) accentColor.copy(alpha = if (isDarkMode) 0.12f else 0.08f) else cardBg,
        label = "ui_style_bg"
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else borderColor,
        label = "ui_style_border"
    )

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 98.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(animatedBg)
            .border(
                width = if (isSelected) 1.6.dp else 1.dp,
                color = animatedBorderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) accentColor.copy(alpha = 0.20f)
                            else textSub.copy(alpha = 0.10f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) accentColor else textSub,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) accentColor else Color.Transparent)
                        .border(
                            width = if (isSelected) 0.dp else 1.5.dp,
                            color = if (isSelected) Color.Transparent else textSub.copy(alpha = 0.35f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    color = textPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = textSub,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DualRealmToggleCard(
    isEnabled: Boolean,
    accentColor: Color,
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color,
    textSub: Color,
    isDarkMode: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val animatedBg by animateColorAsState(
        targetValue = if (isEnabled) accentColor.copy(alpha = if (isDarkMode) 0.10f else 0.06f) else cardBg,
        label = "dual_realm_bg"
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isEnabled) accentColor.copy(alpha = 0.45f) else borderColor,
        label = "dual_realm_border"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(animatedBg)
            .border(
                width = 1.dp,
                color = animatedBorderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onToggle(!isEnabled) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isEnabled) accentColor.copy(alpha = if (isDarkMode) 0.20f else 0.14f)
                    else (if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Layers,
                contentDescription = null,
                tint = if (isEnabled) accentColor else textSub,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Dual Realm Mode",
                    color = textPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (isEnabled) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(accentColor.copy(alpha = 0.15f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "4-Page Swipe",
                            color = accentColor,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = "Separate offline & online decks with 4-page swipe",
                color = textSub,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Switch(
            checked = isEnabled,
            onCheckedChange = { onToggle(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = if (isDarkMode) Color.LightGray else Color.White,
                uncheckedTrackColor = if (isDarkMode) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.12f),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

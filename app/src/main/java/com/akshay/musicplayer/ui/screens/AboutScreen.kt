package com.akshay.musicplayer.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AccentOrange = Color(0xFFFF512F)
private val GradientStart = Color(0xFFFF512F)
private val GradientEnd = Color(0xFFDD2476)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    isDarkMode: Boolean,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    BackHandler(onBack = onBackClick)

    val bgColor = if (isDarkMode) Color(0xFF0F0F0F) else Color(0xFFF2F2F7)
    val cardBg = if (isDarkMode) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color(0xFF6E6E73)
    val dividerColor = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)

    val appVersion = remember {
        val name = BuildConfig.VERSION_NAME
        if (name.startsWith("v", ignoreCase = true)) name else "v$name"
    }
    val gitSha = BuildConfig.GIT_COMMIT_SHA
    val buildTimeFormatted = remember {
        try {
            if (BuildConfig.BUILD_TIME_MILLIS > 0) {
                SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(BuildConfig.BUILD_TIME_MILLIS))
            } else "N/A"
        } catch (_: Exception) {
            "N/A"
        }
    }

    val supportedAbi = remember {
        Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About Mueso", color = textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
            )
        },
        containerColor = bgColor
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)
        ) {
            // ─── 1. Hero Header Banner ───
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(cardBg)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // App Icon with Gradient Background
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(GradientStart, GradientEnd)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Mueso Logo",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    Text(
                        text = "Mueso",
                        color = textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )

                    Text(
                        text = "Fast, Modern & Beautiful Music Experience",
                        color = textSub,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )

                    // Version & Build Badges
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = AccentOrange.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = appVersion,
                                color = AccentOrange,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        if (gitSha.isNotBlank() && gitSha != "dev") {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                                modifier = Modifier.clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Commit SHA", gitSha))
                                    Toast.makeText(context, "Copied commit SHA: $gitSha", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Code,
                                        contentDescription = null,
                                        tint = textSub,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = gitSha,
                                        color = textSub,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ─── 2. Key Features ───
            item {
                Text(
                    text = "Key Features",
                    color = AccentOrange,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .padding(vertical = 6.dp)
                ) {
                    FeatureRow(
                        icon = Icons.Default.GraphicEq,
                        title = "Lossless & High Quality Audio",
                        subtitle = "Adaptive streaming & crystal-clear offline playback",
                        textPrimary = textPrimary,
                        textSub = textSub
                    )
                    HorizontalDivider(color = dividerColor)
                    FeatureRow(
                        icon = Icons.Default.Videocam,
                        title = "Song & Video Switching Mode",
                        subtitle = "Instant toggle between audio and video playback with fullscreen mode",
                        textPrimary = textPrimary,
                        textSub = textSub
                    )
                    HorizontalDivider(color = dividerColor)
                    FeatureRow(
                        icon = Icons.Default.Shield,
                        title = "SponsorBlock Integration",
                        subtitle = "Automatically skip sponsorships, intros, and non-music segments",
                        textPrimary = textPrimary,
                        textSub = textSub
                    )
                    HorizontalDivider(color = dividerColor)
                    FeatureRow(
                        icon = Icons.Default.Lyrics,
                        title = "Synchronized Karaoke Lyrics",
                        subtitle = "Real-time synced lyrics with custom timing offset adjustment",
                        textPrimary = textPrimary,
                        textSub = textSub
                    )
                    HorizontalDivider(color = dividerColor)
                    FeatureRow(
                        icon = Icons.Default.CloudUpload,
                        title = "Google Drive Cloud Backup",
                        subtitle = "Automatic cloud sync for custom playlists and preferences",
                        textPrimary = textPrimary,
                        textSub = textSub
                    )
                }
            }

            // ─── 3. Developer & Links ───
            item {
                Text(
                    text = "Developer & Community",
                    color = AccentOrange,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .padding(vertical = 4.dp)
                ) {
                    LinkRow(
                        icon = Icons.Default.Person,
                        title = "Created by Akshay",
                        subtitle = "@Akshay-86 on GitHub",
                        onClick = {
                            try {
                                uriHandler.openUri("https://github.com/Akshay-86")
                            } catch (_: Exception) {}
                        },
                        textPrimary = textPrimary,
                        textSub = textSub
                    )
                    HorizontalDivider(color = dividerColor)
                    LinkRow(
                        icon = Icons.Default.Code,
                        title = "GitHub Repository",
                        subtitle = "Source code, releases & contributions",
                        onClick = {
                            try {
                                uriHandler.openUri("https://github.com/Akshay-86/Mueso")
                            } catch (_: Exception) {}
                        },
                        textPrimary = textPrimary,
                        textSub = textSub
                    )
                    HorizontalDivider(color = dividerColor)
                    LinkRow(
                        icon = Icons.Default.BugReport,
                        title = "Report an Issue",
                        subtitle = "Suggest features or submit bug reports",
                        onClick = {
                            try {
                                uriHandler.openUri("https://github.com/Akshay-86/Mueso/issues")
                            } catch (_: Exception) {}
                        },
                        textPrimary = textPrimary,
                        textSub = textSub
                    )
                }
            }

            // ─── 4. Build & System Info ───
            item {
                Text(
                    text = "Build & System Information",
                    color = AccentOrange,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    InfoRow("Package Name", context.packageName, textPrimary, textSub)
                    HorizontalDivider(color = dividerColor)
                    InfoRow("Version", appVersion, textPrimary, textSub)
                    HorizontalDivider(color = dividerColor)
                    InfoRow("Commit Hash", gitSha, textPrimary, textSub)
                    HorizontalDivider(color = dividerColor)
                    InfoRow("Architecture ABI", supportedAbi, textPrimary, textSub)
                    HorizontalDivider(color = dividerColor)
                    InfoRow("Build Time", buildTimeFormatted, textPrimary, textSub)
                    HorizontalDivider(color = dividerColor)
                    InfoRow("Android Version", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", textPrimary, textSub)
                }
            }

            // ─── 5. Open Source & License ───
            item {
                Text(
                    text = "License & Credits",
                    color = AccentOrange,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AccentOrange.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("MIT License", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Free and Open Source Software", color = textSub, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Mueso is powered by open source projects and communities, including Jetpack Compose, AndroidX Media3, NewPipeExtractor, SponsorBlock API, Coil, and Room.",
                        color = textSub,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            // ─── 6. Footer ───
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Crafted with passion for music lovers",
                        color = textSub,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "© 2026 Mueso",
                        color = textSub.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    textPrimary: Color,
    textSub: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(AccentOrange.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(20.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(title, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = textSub, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun LinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    textPrimary: Color,
    textSub: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                .background(AccentOrange.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(20.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = textSub, fontSize = 12.sp)
            }
        }
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = textSub, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    textPrimary: Color,
    textSub: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = textSub, fontSize = 13.sp)
        Text(
            value,
            color = textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

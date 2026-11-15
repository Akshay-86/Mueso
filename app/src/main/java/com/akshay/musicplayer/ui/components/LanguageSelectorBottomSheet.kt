package com.akshay.musicplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.domain.models.MusicLanguage
import com.akshay.musicplayer.domain.models.SupportedLanguages
import com.akshay.musicplayer.ui.theme.LocalAccentColor
import com.akshay.musicplayer.ui.theme.LocalIsPureBlack

@Composable
fun LanguageBadge(
    script: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    size: Dp = 26.dp,
    fontSize: TextUnit = 12.sp,
    accentColor: Color = LocalAccentColor.current
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(
                if (isSelected) accentColor
                else accentColor.copy(alpha = 0.16f)
            )
            .border(
                width = 1.dp,
                color = if (isSelected) accentColor else accentColor.copy(alpha = 0.32f),
                shape = RoundedCornerShape(size * 0.28f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = script,
            color = if (isSelected) Color.White else accentColor,
            fontSize = fontSize,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectorBottomSheet(
    selectedLanguage: String,
    isDarkMode: Boolean = true,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isPureBlack = LocalIsPureBlack.current
    val accentColor = LocalAccentColor.current

    val sheetBg = if (isPureBlack) Color(0xFF000000) else if (isDarkMode) Color(0xFF161618) else Color(0xFFFFFFFF)
    val cardBg = if (isPureBlack) Color(0xFF0E0E10) else if (isDarkMode) Color(0xFF1F1F23) else Color(0xFFF2F2F7)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.60f) else Color(0xFF6E6E73)
    val dividerColor = if (isPureBlack) Color.White.copy(alpha = 0.08f) else if (isDarkMode) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)

    var searchQuery by remember { mutableStateOf("") }

    val filteredLanguages = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            SupportedLanguages.ALL
        } else {
            val q = searchQuery.trim().lowercase()
            SupportedLanguages.ALL.filter {
                it.name.lowercase().contains(q) ||
                it.nativeName.lowercase().contains(q) ||
                it.sampleArtists.lowercase().contains(q) ||
                it.nativeScript.lowercase().contains(q)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = if (isDarkMode) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.25f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Music & Content Language",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        Text(
                            text = "Personalizes online hub, charts & lyrics",
                            fontSize = 12.sp,
                            color = textSub
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Search Filter Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Search language (e.g. Telugu, Hindi, Tamil...)",
                        fontSize = 13.sp,
                        color = textSub
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = textSub,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = dividerColor,
                    focusedContainerColor = cardBg,
                    unfocusedContainerColor = cardBg,
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            // Languages List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredLanguages, key = { it.code }) { lang: MusicLanguage ->
                    val isSelected = lang.name.equals(selectedLanguage, ignoreCase = true)
                    val itemBg by animateColorAsState(
                        targetValue = if (isSelected) accentColor.copy(alpha = 0.14f) else cardBg,
                        animationSpec = tween(200),
                        label = "ItemBg"
                    )
                    val itemBorder by animateColorAsState(
                        targetValue = if (isSelected) accentColor.copy(alpha = 0.60f) else Color.Transparent,
                        animationSpec = tween(200),
                        label = "ItemBorder"
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = itemBg,
                        border = BorderStroke(1.dp, itemBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onLanguageSelected(lang.name)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                // Native Script Badge
                                LanguageBadge(
                                    script = lang.nativeScript,
                                    size = 40.dp,
                                    fontSize = 17.sp,
                                    isSelected = isSelected,
                                    accentColor = accentColor
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = lang.name,
                                            fontSize = 15.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            color = textPrimary
                                        )
                                        Text(
                                            text = "(${lang.nativeName})",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isSelected) accentColor else textSub
                                        )
                                    }
                                    if (lang.sampleArtists.isNotBlank()) {
                                        Text(
                                            text = lang.sampleArtists,
                                            fontSize = 11.sp,
                                            color = textSub,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }

                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(accentColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .border(1.5.dp, textSub.copy(alpha = 0.35f), CircleShape)
                                )
                            }
                        }
                    }
                }

                if (filteredLanguages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.SearchOff,
                                    contentDescription = null,
                                    tint = textSub,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No languages matching \"$searchQuery\"",
                                    fontSize = 13.sp,
                                    color = textSub
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

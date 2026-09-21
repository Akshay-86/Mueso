package com.akshay.musicplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.ui.theme.LocalAccentColor
import com.akshay.musicplayer.ui.theme.LocalIsPureBlack

enum class ClassicNavTab(val title: String, val icon: ImageVector) {
    LIBRARY("Library", Icons.Default.LibraryMusic),
    EXPLORE("Explore", Icons.Default.Explore),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun ClassicBottomBar(
    currentTab: ClassicNavTab,
    isDarkMode: Boolean = true,
    onTabSelected: (ClassicNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val isPureBlack = LocalIsPureBlack.current
    val bgColor = if (isPureBlack) Color(0xFF000000) else if (isDarkMode) Color(0xFF14141E) else Color(0xFFFFFFFF)
    val inactiveColor = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF8E8E93)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .navigationBarsPadding()
            .padding(vertical = 4.dp, horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClassicNavTab.entries.forEach { tab ->
                val isSelected = tab == currentTab
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) accent else inactiveColor,
                    animationSpec = tween(250),
                    label = "tabColor"
                )

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isSelected) accent.copy(alpha = 0.15f) else Color.Transparent)
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = contentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = tab.title,
                        color = contentColor,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

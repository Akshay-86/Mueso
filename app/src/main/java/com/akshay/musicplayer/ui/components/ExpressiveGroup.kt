package com.akshay.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.akshay.musicplayer.ui.theme.ExpressiveShapes

/**
 * Material 3 Expressive Connected Group Card
 *
 * Renders a list of items inside a continuous connected container where:
 * - Top card has rounded top corners (24dp) and flat/subtle bottom corners (4dp).
 * - Middle cards have subtle corners (4dp).
 * - Bottom card has flat/subtle top corners (4dp) and rounded bottom corners (24dp).
 * - Single item has full rounded corners (24dp).
 */
@Composable
fun ExpressiveGroup(
    modifier: Modifier = Modifier,
    containerColor: Color,
    dividerColor: Color = Color.Transparent,
    itemSpacing: Dp = 2.dp,
    content: @Composable ExpressiveGroupScope.() -> Unit
) {
    val scope = ExpressiveGroupScopeImpl()
    scope.content()

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        val items = scope.items
        items.forEachIndexed { index, itemContent ->
            val shape = ExpressiveShapes.forGroupIndex(index, items.size)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(containerColor)
            ) {
                itemContent()
            }
            if (index < items.size - 1) {
                if (dividerColor != Color.Transparent) {
                    HorizontalDivider(color = dividerColor)
                } else if (itemSpacing > 0.dp) {
                    Spacer(modifier = Modifier.height(itemSpacing))
                }
            }
        }
    }
}

interface ExpressiveGroupScope {
    fun item(content: @Composable () -> Unit)
}

private class ExpressiveGroupScopeImpl : ExpressiveGroupScope {
    val items = mutableListOf<@Composable () -> Unit>()

    override fun item(content: @Composable () -> Unit) {
        items.add(content)
    }
}

package com.akshay.musicplayer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Design System Shapes
 *
 * Implements Google's Connected Group Containers & 28dp Pill Tokens.
 */
object ExpressiveShapes {

    val PillCorner: Dp = 28.dp
    val GroupOuterCorner: Dp = 24.dp
    val GroupInnerCorner: Dp = 4.dp

    val FullPill = RoundedCornerShape(percent = 50)
    val ExtraLargePill = RoundedCornerShape(PillCorner)
    val LargePill = RoundedCornerShape(20.dp)
    val MediumPill = RoundedCornerShape(16.dp)
    val SmallPill = RoundedCornerShape(12.dp)

    /**
     * Connected Group Container Shapes:
     * - Top item: Outer rounded top corners (24dp), inner flat/subtle bottom corners (4dp)
     * - Middle items: Subtle inner corners (4dp)
     * - Bottom item: Inner subtle top corners (4dp), outer rounded bottom corners (24dp)
     * - Single item: Full outer rounded corners (24dp)
     */
    fun topGroupItem(outer: Dp = GroupOuterCorner, inner: Dp = GroupInnerCorner) =
        RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)

    fun middleGroupItem(inner: Dp = GroupInnerCorner) =
        RoundedCornerShape(inner)

    fun bottomGroupItem(outer: Dp = GroupOuterCorner, inner: Dp = GroupInnerCorner) =
        RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)

    fun singleGroupItem(outer: Dp = GroupOuterCorner) =
        RoundedCornerShape(outer)

    fun forGroupIndex(index: Int, totalCount: Int, outer: Dp = GroupOuterCorner, inner: Dp = GroupInnerCorner): RoundedCornerShape {
        return when {
            totalCount <= 1 -> singleGroupItem(outer)
            index == 0 -> topGroupItem(outer, inner)
            index == totalCount - 1 -> bottomGroupItem(outer, inner)
            else -> middleGroupItem(inner)
        }
    }
}

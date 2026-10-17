package com.safemode.hbconnect.ui.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws a thin scrollbar thumb down the right edge of a LazyColumn so it's clear more items are
 * below. Sizes and positions the thumb from the average item height, and hides it when everything
 * already fits.
 */
fun Modifier.verticalScrollbar(
    state: LazyListState,
    color: Color,
    thickness: Dp = 4.dp,
): Modifier = drawWithContent {
    drawContent()
    val info = state.layoutInfo
    val visible = info.visibleItemsInfo
    val total = info.totalItemsCount
    if (visible.isEmpty() || total == 0) return@drawWithContent

    val avgItem = visible.sumOf { it.size }.toFloat() / visible.size
    val contentHeight = avgItem * total
    val viewportHeight = size.height
    if (contentHeight <= viewportHeight) return@drawWithContent // everything fits; no bar

    val first = visible.first()
    val scrolled = first.index * avgItem - first.offset
    val minThumb = 24.dp.toPx()
    val thumbHeight = (viewportHeight * viewportHeight / contentHeight).coerceAtLeast(minThumb)
    val maxOffset = viewportHeight - thumbHeight
    val thumbTop = (scrolled / contentHeight * viewportHeight).coerceIn(0f, maxOffset)
    val barWidth = thickness.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width - barWidth, thumbTop),
        size = Size(barWidth, thumbHeight),
        cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
    )
}

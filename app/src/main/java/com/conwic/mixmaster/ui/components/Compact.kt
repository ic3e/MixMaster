package com.conwic.mixmaster.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Whether the window is short enough that height is the scarce thing.
 *
 * A phone on its side has about a third of the height it has upright, and every gap in this app
 * was set for upright: turned sideways, the title, the tab strip and two cards filled the
 * screen and the rest of the page was a scroll away. Under this the chrome is set tighter —
 * the padding above and below things, not the things themselves — so more of the page fits
 * without anything getting smaller to read.
 *
 * 480dp because a phone in landscape is around 360–430dp tall and a phone upright is 700 and
 * up; nothing a crew owns lands in between.
 */
@Composable
fun compactHeight(): Boolean = LocalConfiguration.current.screenHeightDp < 480

/** [tight] when the window is short, [roomy] when it is not. */
@Composable
fun byHeight(tight: Dp, roomy: Dp): Dp = if (compactHeight()) tight else roomy

/**
 * The widest a column of this app is allowed to get.
 *
 * A phone on its side is better than twice as wide as it is upright, and a card stretched across
 * all of it stops being a card: a paragraph of scope notes runs to a line nobody can track back
 * from, and a row of two words with a figure on the end is mostly a hole in the middle. Sixty
 * characters or so is what a line wants, and this is about that at the app's body size.
 */
private val PageMaxWidth = 640.dp

/**
 * The page's own margins — the usual ones upright, and enough to hold the column to
 * [PageMaxWidth] on a window wider than that.
 *
 * Put on the list's content rather than on the list itself, so the page stays scrollable right
 * out to the edges of the glass: on a wide screen the margins are a good part of the reach, and
 * a swipe that lands there should still move the page.
 */
@Composable
fun pagePadding(top: Dp = 20.dp, bottom: Dp = 20.dp, side: Dp = 20.dp): PaddingValues {
    val width = LocalConfiguration.current.screenWidthDp.dp
    val margin = maxOf(side, (width - PageMaxWidth) / 2)
    return PaddingValues(start = margin, end = margin, top = top, bottom = bottom)
}

/**
 * How much of the bottom of a top-level page the tab bar covers. The bar is see-through and the
 * page runs on underneath it, so a page's list ends this much further down to let its last card
 * scroll clear, and anything floating at the bottom lifts by it. Nothing wherever there is no bar.
 */
val LocalBarInset = compositionLocalOf { 0.dp }

/** The same margin on its own, for chrome that has to line up with the page under it. */
@Composable
fun pageSide(side: Dp = 20.dp): Dp {
    val width = LocalConfiguration.current.screenWidthDp.dp
    return maxOf(side, (width - PageMaxWidth) / 2)
}

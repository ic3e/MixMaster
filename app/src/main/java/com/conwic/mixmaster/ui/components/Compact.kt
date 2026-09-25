package com.conwic.mixmaster.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp

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

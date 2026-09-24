package com.conwic.mixmaster.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.conwic.mixmaster.R

// Proportions taken straight off the supplied artwork, so the badge, the gap and the wordmark
// keep their relationship at any size.
private const val BadgeWidth = 406f
private const val LogoHeight = 246f
private const val Gap = 37f
private const val WordmarkWidth = 1459f
private const val WordmarkHeight = 245f


/**
 * The full ConWiC lockup: the badge, then CONWIC over FLOORS FOR LIVING.
 *
 * The wordmark is a single-colour vector so it can be tinted to suit whatever it sits on; the
 * badge keeps the brand brown either way.
 */
@Composable
fun ConwicLockup(
    modifier: Modifier = Modifier,
    height: Dp = 34.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(id = R.drawable.conwic_badge),
            contentDescription = "ConWiC",
            modifier = Modifier
                .height(height)
                .aspectRatio(BadgeWidth / LogoHeight),
        )
        Image(
            painter = painterResource(id = R.drawable.conwic_wordmark),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier
                .padding(start = height * (Gap / LogoHeight))
                .height(height * (WordmarkHeight / LogoHeight))
                .aspectRatio(WordmarkWidth / WordmarkHeight),
        )
    }
}

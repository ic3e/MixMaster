package com.conwic.mixmaster.ui.theme

import androidx.compose.ui.graphics.Color

// ConWiC brand palette — these are the exact token values from the MixMaster design
// prototype's theme.css, so the app and the design read as the same product.
val Accent = Color(0xFF8A5A2E)
val Accent2 = Color(0xFFB98A3E)
val Charcoal = Color(0xFF313538)
val CharcoalDeep = Color(0xFF222629)
val Danger = Color(0xFFB3423A)
val Ok = Color(0xFF4B7A52)

// The design's translucent tints, flattened over the page background so they can be used
// as solid fills.
val AccentDim = Color(0xFFEBE6E0)
val Accent2Dim = Color(0xFFECE5D7)
val OkDim = Color(0xFFDEE5DD)
val DangerDim = Color(0xFFEEE0DE)

val BgCream = Color(0xFFF6F6F4)
val BgSoft = Color(0xFFEFECE5)
val Surface = Color(0xFFFFFFFF)

val TextPrimary = Color(0xFF141311)
val TextDim = Color(0xFF6C6459)
val TextFaint = Color(0xFF9C9488)
val TextOnDark = Color(0xFFF3F1EC)

val BorderSoft = Color(0xFFEBE7DE)
val Border = Color(0xFFE4DFD4)

// Status pills — background/foreground pairs straight from the design's .pill-* rules.
val PillActiveBg = AccentDim
val PillActiveText = Accent
val PillPlanningBg = Accent2Dim
val PillPlanningText = Color(0xFF8A6526)
val PillHoldBg = BgSoft
val PillHoldText = TextFaint
val PillDoneBg = OkDim
val PillDoneText = Ok

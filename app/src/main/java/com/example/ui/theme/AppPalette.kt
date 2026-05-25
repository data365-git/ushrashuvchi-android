package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class AppPaletteSet(
    val background: Color,
    val surface: Color,
    val cardGradientStart: Color,
    val cardGradientEnd: Color,
    val outlineSoft: Color,
    val outlineStrong: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val onSurfaceSubtle: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val errorBg: Color,
    val errorFg: Color,
    val warningBg: Color,
    val warningFg: Color,
    val successBg: Color,
    val successFg: Color,
    val fabBg: Color,
    val fabFg: Color,
    val fabShadow: Color,
    val cardShadowAmbient: Color,
    val cardShadowSpot: Color,
    val navBarBg: Color,
    val settingsRowBg: Color,
    val settingsRowBorder: Color,
    val accent: Color,
    val recordingBadgeBg: Color,
    val recordingBadgeFg: Color,
    val discard: Color,
    val controlGray: Color,
    val controlGrayFg: Color,
    val waveformBar: Color,
    val mutedLabel: Color,
    // Library-design palette additions
    val folderPurple: Color,
    val folderPink: Color,
    val folderGreen: Color,
    val folderAmber: Color,
    val folderBlue: Color,
    val onSurfaceVariant: Color,
    val secondary: Color,
    val outlineSoftSecondary: Color,
    val selectionBg: Color,
    val shadowCardPrimary: Color,
    val shadowCardSpread: Color,
)

val LightAppPalette = AppPaletteSet(
    background         = Color(0xFFF1F5F9),
    surface            = Color(0xFFFFFFFF),
    cardGradientStart  = Color(0xFFFFFFFF),
    cardGradientEnd    = Color(0xFFF8FAFC),
    outlineSoft        = Color(0xFFE2E8F0),
    outlineStrong      = Color(0xFFCBD5E1),
    onSurface          = Color(0xFF0F172A),
    onSurfaceMuted     = Color(0xFF475569),
    onSurfaceSubtle    = Color(0xFF94A3B8),
    primary            = Color(0xFF0F172A),
    onPrimary          = Color(0xFFFFFFFF),
    primaryContainer   = Color(0xFF1E293B),
    onPrimaryContainer = Color(0xFFF8FAFC),
    errorBg            = Color(0xFFFFE4E6),
    errorFg            = Color(0xFFB91C1C),
    warningBg          = Color(0xFFFEF9C3),
    warningFg          = Color(0xFF92400E),
    successBg          = Color(0xFFDCFCE7),
    successFg          = Color(0xFF166534),
    fabBg              = Color(0xFF0F172A),
    fabFg              = Color(0xFFFFFFFF),
    fabShadow          = Color(0x660F172A),
    cardShadowAmbient  = Color(0x14000000),
    cardShadowSpot     = Color(0x29000000),
    navBarBg           = Color(0xF2FFFFFF),
    settingsRowBg      = Color(0xFFFFFFFF),
    settingsRowBorder  = Color(0xFFE2E8F0),
    accent             = Color(0xFF1D4ED8),
    recordingBadgeBg   = Color(0xFFFEE2E2),
    recordingBadgeFg   = Color(0xFFDC2626),
    discard            = Color(0xFFEF4444),
    controlGray        = Color(0xFFE2E8F0),
    controlGrayFg      = Color(0xFF334155),
    waveformBar        = Color(0xFF1D4ED8),
    mutedLabel         = Color(0xFF64748B),
    folderPurple        = Color(0xFF8B5CF6),
    folderPink          = Color(0xFFEC4899),
    folderGreen         = Color(0xFF006242),
    folderAmber         = Color(0xFFF59E0B),
    folderBlue          = Color(0xFF1D4ED8),
    onSurfaceVariant    = Color(0xFF334155),
    secondary           = Color(0xFF475569),
    outlineSoftSecondary = Color(0xFFE2E8F0),
    selectionBg         = Color(0xFF0F172A).copy(alpha = 0.05f),
    shadowCardPrimary   = Color.Black.copy(alpha = 0.05f),
    shadowCardSpread    = Color.Black.copy(alpha = 0.10f),
)

val DarkAppPalette = AppPaletteSet(
    background         = Color(0xFF0B1220),
    surface            = Color(0xFF111827),
    cardGradientStart  = Color(0xFF1A2333),
    cardGradientEnd    = Color(0xFF111827),
    outlineSoft        = Color(0xFF1F2937),
    outlineStrong      = Color(0xFF374151),
    onSurface          = Color(0xFFF8FAFC),
    onSurfaceMuted     = Color(0xFF94A3B8),
    onSurfaceSubtle    = Color(0xFF64748B),
    primary            = Color(0xFFE2E8F0),
    onPrimary          = Color(0xFF0F172A),
    primaryContainer   = Color(0xFF1E293B),
    onPrimaryContainer = Color(0xFFF8FAFC),
    errorBg            = Color(0xFF450A0A),
    errorFg            = Color(0xFFFCA5A5),
    warningBg          = Color(0xFF451A03),
    warningFg          = Color(0xFFFDE68A),
    successBg          = Color(0xFF052E16),
    successFg          = Color(0xFF86EFAC),
    fabBg              = Color(0xFFE2E8F0),
    fabFg              = Color(0xFF0F172A),
    fabShadow          = Color(0x99000000),
    cardShadowAmbient  = Color(0x33000000),
    cardShadowSpot     = Color(0x4D000000),
    navBarBg           = Color(0xF0111827),
    settingsRowBg      = Color(0xFF1A2333),
    settingsRowBorder  = Color(0xFF1F2937),
    accent             = Color(0xFF3B82F6),
    recordingBadgeBg   = Color(0xFF7F1D1D),
    recordingBadgeFg   = Color(0xFFFCA5A5),
    discard            = Color(0xFFEF4444),
    controlGray        = Color(0xFF1E293B),
    controlGrayFg      = Color(0xFFCBD5E1),
    waveformBar        = Color(0xFF3B82F6),
    mutedLabel         = Color(0xFF94A3B8),
    folderPurple        = Color(0xFFB4A0FF),
    folderPink          = Color(0xFFFCA5D2),
    folderGreen         = Color(0xFF4ADE80),
    folderAmber         = Color(0xFFFBBF24),
    folderBlue          = Color(0xFF3B82F6),
    onSurfaceVariant    = Color(0xFFCBD5E1),
    secondary           = Color(0xFF94A3B8),
    outlineSoftSecondary = Color(0xFF334155),
    selectionBg         = Color(0xFF3B82F6).copy(alpha = 0.15f),
    shadowCardPrimary   = Color.Black.copy(alpha = 0.15f),
    shadowCardSpread    = Color.Black.copy(alpha = 0.30f),
)

val LocalAppPalette = staticCompositionLocalOf<AppPaletteSet> { LightAppPalette }

fun Modifier.elevatedCardBg(
    cornerRadius: Dp = 20.dp,
    elevation: Dp = 8.dp
): Modifier = composed {
    val p = LocalAppPalette.current
    val shape = RoundedCornerShape(cornerRadius)
    val brush = remember(p) {
        Brush.linearGradient(listOf(p.cardGradientStart, p.cardGradientEnd))
    }
    this
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.05f),
            spotColor = Color.Black.copy(alpha = 0.10f),
            clip = false
        )
        .clip(shape)
        .background(brush = brush, shape = shape)
        .border(1.dp, Color.White.copy(alpha = 0.5f), shape)
}

/** Map a folder's stored colorHex to one of the design palette colors. */
@Composable
fun resolveFolderTint(colorHex: String?): Color {
    val p = LocalAppPalette.current
    return when (colorHex?.lowercase()?.removePrefix("#")?.take(6)) {
        "8b5cf6", "9333ea", "a855f7" -> p.folderPurple
        "ec4899", "db2777", "f472b6" -> p.folderPink
        "006242", "10b981", "059669" -> p.folderGreen
        "f59e0b", "d97706", "fbbf24" -> p.folderAmber
        "1d4ed8", "2563eb", "3b82f6" -> p.folderBlue
        null -> p.folderBlue
        else -> try {
            Color(android.graphics.Color.parseColor("#${colorHex.removePrefix("#")}"))
        } catch (_: Exception) { p.folderBlue }
    }
}

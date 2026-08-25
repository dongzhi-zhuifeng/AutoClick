package com.Luofeng.autoclick

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class AppColorScheme(
    val Background: Color,
    val CardBackground: Color,
    val PrimaryBlue: Color,
    val LightBlueTint: Color,
    val LightBlueTintDeep: Color,
    val TextPrimary: Color,
    val TextSecondary: Color,
    val Danger: Color,
    val DangerLight: Color,
    val Warning: Color,
    val WarningLight: Color,
    val Success: Color,
    val PillOnBg: Color,
    val PillOnText: Color,
    val PillOffBg: Color,
    val PillOffText: Color,
    val Divider: Color,
    val DividerOnCard: Color,
    val OnPrimary: Color,
    val FabContainer: Color,
    val FabContent: Color,
    val FabPressed: Color
)

private val LightBlueTintLight = Color(0xFFE8EEF6)
private val PrimaryBlueLight = Color(0xFF0041A5)
private val TextSecondaryLight = Color(0xFF6C6C70)

val AppColors = AppColorScheme(
    Background = Color(0xFFF5F5F5),
    CardBackground = Color(0xFFFFFFFF),
    PrimaryBlue = PrimaryBlueLight,
    LightBlueTint = LightBlueTintLight,
    LightBlueTintDeep = Color(0xFFD6E0F0),
    TextPrimary = Color(0xFF1C1C1E),
    TextSecondary = TextSecondaryLight,
    Danger = Color(0xFFD70015),
    DangerLight = Color(0xFFFFEDEC),
    Warning = Color(0xFFFF9500),
    WarningLight = Color(0xFFFFF4E5),
    Success = Color(0xFF34C759),
    PillOnBg = LightBlueTintLight,
    PillOnText = PrimaryBlueLight,
    PillOffBg = Color(0xFFF0F0F2),
    PillOffText = TextSecondaryLight,
    Divider = Color(0xFFE0E0E0),
    DividerOnCard = Color(0xFFD6D6D6),
    OnPrimary = Color(0xFFFFFFFF),
    FabContainer = LightBlueTintLight,
    FabContent = Color(0xFF2D2D2D),
    FabPressed = Color(0xFFD6E0F0)
)

private val PrimaryBlueDark = Color(0xFF0A84FF)
private val TextSecondaryDark = Color(0xFFA1A1A6)
private val LightBlueTintDark = Color(0xFF1C3A5F)

val AppColorsDark = AppColorScheme(
    Background = Color(0xFF000000),
    CardBackground = Color(0xFF1C1C1E),
    PrimaryBlue = PrimaryBlueDark,
    LightBlueTint = LightBlueTintDark,
    LightBlueTintDeep = Color(0xFF2C4A6F),
    TextPrimary = Color(0xFFF5F5F7),
    TextSecondary = TextSecondaryDark,
    Danger = Color(0xFFFF6961),
    DangerLight = Color(0xFF3A1515),
    Warning = Color(0xFFFF9F0A),
    WarningLight = Color(0xFF3A2A10),
    Success = Color(0xFF30D158),
    PillOnBg = LightBlueTintDark,
    PillOnText = PrimaryBlueDark,
    PillOffBg = Color(0xFF2C2C2E),
    PillOffText = TextSecondaryDark,
    Divider = Color(0xFF3A3A3C),
    DividerOnCard = Color(0xFF3A3A3C),
    OnPrimary = Color(0xFFFFFFFF),
    FabContainer = LightBlueTintDark,
    FabContent = Color(0xFFF5F5F7),
    FabPressed = Color(0xFF2C4A6F)
)

val LocalAppColors = staticCompositionLocalOf { AppColors }

object AppShapes {
    val cardShape = RoundedCornerShape(16.dp)
    val dialogShape = RoundedCornerShape(20.dp)
    val buttonShape = RoundedCornerShape(12.dp)
    val pillShape = RoundedCornerShape(percent = 50)
    val menuShape = RoundedCornerShape(12.dp)
    val iconCircleShape = RoundedCornerShape(percent = 50)
    val inputShape = RoundedCornerShape(12.dp)
    val fabShape = RoundedCornerShape(12.dp)
}

object AppSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val cardGap = 16.dp
    val iconSize = 48.dp
    val menuItemHeight = 48.dp
    val pillHPad = 16.dp
    val pillVPad = 8.dp
    val pageHPad = 20.dp
    val clockTop = 64.dp
    val clockTopAnalog = 28.dp
    val clockBottom = 32.dp
    val clockCaptionGap = 12.dp
    val fabListClearance = 102.dp
    val overlayHintTop = 60.dp
}

object AppDuration {
    const val stateMs = 220
    const val clockToggleMs = 280
}

object AppTypeScale {
    const val titleSp = 20
    const val sectionSp = 16
    const val bodySp = 16
    const val menuSp = 14
    const val captionSp = 12
    const val clockFactor = 0.21f
    const val clockMinSp = 36f
    const val clockMaxSp = 96f
    const val analogFactor = 0.56f
    const val analogMinDp = 168f
    const val analogMaxDp = 248f
}

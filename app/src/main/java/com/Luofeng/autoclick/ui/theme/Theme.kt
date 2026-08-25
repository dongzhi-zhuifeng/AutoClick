package com.Luofeng.autoclick.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.Luofeng.autoclick.AppColors
import com.Luofeng.autoclick.AppColorsDark
import com.Luofeng.autoclick.LocalAppColors

@Composable
fun AutoClickTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) AppColorsDark else AppColors
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.PrimaryBlue,
            onPrimary = colors.OnPrimary,
            background = colors.Background,
            surface = colors.CardBackground,
            onBackground = colors.TextPrimary,
            onSurface = colors.TextPrimary,
            error = colors.Danger
        )
    } else {
        lightColorScheme(
            primary = colors.PrimaryBlue,
            onPrimary = colors.OnPrimary,
            background = colors.Background,
            surface = colors.CardBackground,
            onBackground = colors.TextPrimary,
            onSurface = colors.TextPrimary,
            error = colors.Danger
        )
    }

    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class AppThemeType(
    val displayName: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val backgroundColor: Color,
    val surfaceColor: Color,
    val onPrimary: Color,
    val onBackground: Color,
    val onSurface: Color,
    val isLight: Boolean = false
) {
    COBALT_MIDNIGHT(
        displayName = "Cobalt Midnight",
        primaryColor = Color(0xFF3B82F6),
        secondaryColor = Color(0xFF60A5FA),
        backgroundColor = Color(0xFF0F172A),
        surfaceColor = Color(0xFF1E293B),
        onPrimary = Color.White,
        onBackground = Color(0xFFF1F5F9),
        onSurface = Color(0xFFE2E8F0)
    ),
    EMERALD_FOREST(
        displayName = "Emerald Forest",
        primaryColor = Color(0xFF10B981),
        secondaryColor = Color(0xFF34D399),
        backgroundColor = Color(0xFF063B2F),
        surfaceColor = Color(0xFF0F5A49),
        onPrimary = Color.White,
        onBackground = Color(0xFFECFDF5),
        onSurface = Color(0xFFD1FAE5)
    ),
    SUNSET_COPPER(
        displayName = "Sunset Copper",
        primaryColor = Color(0xFFF59E0B),
        secondaryColor = Color(0xFFFBBF24),
        backgroundColor = Color(0xFF2D1603),
        surfaceColor = Color(0xFF5A2C06),
        onPrimary = Color.White,
        onBackground = Color(0xFFFEF3C7),
        onSurface = Color(0xFFFDE68A)
    ),
    LAVENDER_FIELDS(
        displayName = "Lavender Fields",
        primaryColor = Color(0xFF8B5CF6),
        secondaryColor = Color(0xFFA78BFA),
        backgroundColor = Color(0xFF1E0B36),
        surfaceColor = Color(0xFF3C176D),
        onPrimary = Color.White,
        onBackground = Color(0xFFF5F3FF),
        onSurface = Color(0xFFEDE9FE)
    ),
    CYBERPUNK_NEON(
        displayName = "Cyberpunk Neon",
        primaryColor = Color(0xFFEC4899),
        secondaryColor = Color(0xFFF472B6),
        backgroundColor = Color(0xFF09090B),
        surfaceColor = Color(0xFF18181B),
        onPrimary = Color.White,
        onBackground = Color(0xFF34D399),
        onSurface = Color(0xFF2DD4BF)
    ),
    CAPPUCCINO_ROAST(
        displayName = "Cozy Cappuccino",
        primaryColor = Color(0xFFD9A05B),
        secondaryColor = Color(0xFFC58A43),
        backgroundColor = Color(0xFF1B120C),
        surfaceColor = Color(0xFF2F2117),
        onPrimary = Color(0xFF1B120C),
        onBackground = Color(0xFFFFF0E6),
        onSurface = Color(0xFFF5D6C1)
    ),
    CATPPUCCIN_MOCHA(
        displayName = "Catppuccin Mocha",
        primaryColor = Color(0xFFCBA6F7),
        secondaryColor = Color(0xFF89B4FA),
        backgroundColor = Color(0xFF1E1E2E),
        surfaceColor = Color(0xFF181825),
        onPrimary = Color(0xFF11111B),
        onBackground = Color(0xFFCDD6F4),
        onSurface = Color(0xFFBAC2DE)
    ),
    CATPPUCCIN_MACCHIATO(
        displayName = "Catppuccin Macchiato",
        primaryColor = Color(0xFFF5BDE6),
        secondaryColor = Color(0xFF8AADF4),
        backgroundColor = Color(0xFF24273A),
        surfaceColor = Color(0xFF1E2030),
        onPrimary = Color(0xFF181926),
        onBackground = Color(0xFFCAD3F5),
        onSurface = Color(0xFFB8C0E0)
    ),
    CATPPUCCIN_FRAPPE(
        displayName = "Catppuccin Frappé",
        primaryColor = Color(0xFFCA9EE6),
        secondaryColor = Color(0xFF8CAAEE),
        backgroundColor = Color(0xFF303446),
        surfaceColor = Color(0xFF292C3C),
        onPrimary = Color(0xFF232634),
        onBackground = Color(0xFFC6D0F5),
        onSurface = Color(0xFFB5BFE2)
    ),
    CATPPUCCIN_LATTE(
        displayName = "Catppuccin Latte",
        primaryColor = Color(0xFF8839EF),
        secondaryColor = Color(0xFF1E66F5),
        backgroundColor = Color(0xFFEFF1F5),
        surfaceColor = Color(0xFFE6E9EF),
        onPrimary = Color.White,
        onBackground = Color(0xFF4C4F69),
        onSurface = Color(0xFF5C5F77),
        isLight = true
    );

    val colorScheme = if (isLight) {
        lightColorScheme(
            primary = primaryColor,
            secondary = secondaryColor,
            background = backgroundColor,
            surface = surfaceColor,
            onPrimary = onPrimary,
            onBackground = onBackground,
            onSurface = onSurface
        )
    } else {
        darkColorScheme(
            primary = primaryColor,
            secondary = secondaryColor,
            background = backgroundColor,
            surface = surfaceColor,
            onPrimary = onPrimary,
            onBackground = onBackground,
            onSurface = onSurface
        )
    }
}

@Composable
fun SilentRemindersTheme(
    themeType: AppThemeType = AppThemeType.COBALT_MIDNIGHT,
    customColor: Color? = null,
    fontFamilyName: String = "DEFAULT",
    content: @Composable () -> Unit
) {
    val baseScheme = themeType.colorScheme
    val finalScheme = if (customColor != null) {
        darkColorScheme(
            primary = customColor,
            secondary = customColor.copy(alpha = 0.7f),
            background = Color(0xFF0F172A), // Keep deep midnight atmosphere
            surface = Color(0xFF1E293B),    // Same consistent slate surface
            onPrimary = Color.White,
            onBackground = Color(0xFFF1F5F9),
            onSurface = Color(0xFFE2E8F0)
        )
    } else {
        baseScheme
    }

    val fontFamily = getFontFamilyByName(fontFamilyName)
    val typography = getTypography(fontFamily)

    MaterialTheme(
        colorScheme = finalScheme,
        typography = typography,
        content = content
    )
}

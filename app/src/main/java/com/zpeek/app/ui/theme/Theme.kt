package com.zpeek.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// 品牌色：蓝紫主色 + 青绿辅色；深色模式使用更柔和的色调以减少刺眼感
private val BrandBlue = Color(0xFF2F6BFF)
private val BrandBlueDeep = Color(0xFF1B4FD8)
private val BrandBlueSoft = Color(0xFFE8EFFF)
private val BrandTeal = Color(0xFF0FB5A3)
private val Danger = Color(0xFFE5484D)
private val DangerDark = Color(0xFFFF6B6E)

private val LightTextPrimary = Color(0xFF161A22)
private val LightTextSecondary = Color(0xFF6B7280)
private val LightBg = Color(0xFFF7F8FC)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceDim = Color(0xFFEFF1F7)
private val LightOutline = Color(0xFFDDE1EC)

private val DarkTextPrimary = Color(0xFFF2F4F8)
private val DarkTextSecondary = Color(0xFFA2A9B8)
private val DarkBg = Color(0xFF0E1015)
private val DarkSurface = Color(0xFF171A21)
private val DarkSurfaceDim = Color(0xFF1F232C)
private val DarkOutline = Color(0xFF2C313D)

val ZipPeekBrand = BrandBlue
val ZipPeekTeal = BrandTeal

private val LightScheme = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = BrandBlueSoft,
    onPrimaryContainer = BrandBlueDeep,
    secondary = BrandTeal,
    onSecondary = Color.White,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceDim,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
    outlineVariant = LightOutline,
    error = Danger,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7FA8FF),
    onPrimary = Color(0xFF0B1B3D),
    primaryContainer = Color(0xFF1B2440),
    onPrimaryContainer = Color(0xFFBFD3FF),
    secondary = Color(0xFF3ED9C6),
    onSecondary = Color(0xFF00332E),
    background = DarkBg,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceDim,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = DangerDark,
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp),
)

@Composable
fun ZipPeekTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (dark) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        val context = LocalContext.current
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}

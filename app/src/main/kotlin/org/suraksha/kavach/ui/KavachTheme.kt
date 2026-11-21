package org.suraksha.kavach.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Forest = Color(0xFF176B52)
val Ink = Color(0xFF173B30)
val Cream = Color(0xFFF7F8F3)
val Danger = Color(0xFF991B1B)

@Composable
fun KavachTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            secondary = Forest, onSecondary = Color.White, secondaryContainer = Color(0xFFDCEFE5),
            onSecondaryContainer = Ink, tertiary = Forest, outline = Color(0xFF778A80),
            primary = Forest, onPrimary = Color.White, primaryContainer = Color(0xFFDCEFE5),
            onPrimaryContainer = Ink, background = Cream, onBackground = Ink,
            surface = Color.White, onSurface = Ink, surfaceVariant = Color(0xFFEDF1E9),
            onSurfaceVariant = Color(0xFF4E6259), error = Danger
        ),
        typography = Typography(
            headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
            headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
            titleLarge = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = TextStyle(fontSize = 21.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = TextStyle(fontSize = 20.sp, lineHeight = 29.sp),
            bodyMedium = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
            labelLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
        ), content = content
    )
}

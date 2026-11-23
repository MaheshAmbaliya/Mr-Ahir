package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Brand Accent Colors
val CyanGlow = Color(0xFF00D2FF)
val ElectricBlue = Color(0xFF3B82F6)
val VividFuchsia = Color(0xFFC026D3)
val NeonPink = Color(0xFFEC4899)
val StarGold = Color(0xFFFBBF24)
val ErrorCoral = Color(0xFFF87171)
val ErrorSoft = Color(0xFFFCA5A5)

// Dark Theme Palette (OLED Cinema)
val DarkBackground = Color(0xFF000000)
val DarkSurface = Color(0xFF14141A)
val DarkSurfaceVariant = Color(0xFF1C1C26)
val DarkTileStage = Color(0xFF06060B)
val DarkOutline = Color(0xFF262632)
val DarkOnSurface = Color(0xFFFFFFFF)
val DarkOnSurfaceMuted = Color(0xFF94A3B8)

// Light Theme Palette (Crisp Studio)
val LightBackground = Color(0xFFF4F6FB)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEBF0F8)
val LightTileStage = Color(0xFF0A0B12)
val LightOutline = Color(0xFFDFE4EE)
val LightOnSurface = Color(0xFF0F172A)
val LightOnSurfaceMuted = Color(0xFF64748B)

val PlusTvBrandGradient = Brush.horizontalGradient(
    colors = listOf(CyanGlow, ElectricBlue, VividFuchsia, NeonPink)
)

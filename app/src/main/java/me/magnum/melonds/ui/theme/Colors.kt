package me.magnum.melonds.ui.theme

import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import me.magnum.melonds.R

val uncheckedThumbColor: Color @Composable get() = colorResource(id = R.color.switchThumbUnselected)
val gameMasteryColor: Color get() = Color(0xFFFFD700)

val LightMelonColors @Composable get() = lightColors(
    primary = colorResource(R.color.glass_accent),
    primaryVariant = colorResource(R.color.glass_background),
    secondary = colorResource(R.color.glass_accent),
    secondaryVariant = colorResource(R.color.glass_accent),
    background = colorResource(R.color.glass_background),
    surface = colorResource(R.color.glass_surface),
    onPrimary = colorResource(R.color.glass_on_accent),
    onSecondary = colorResource(R.color.glass_on_accent),
    onSurface = colorResource(R.color.glass_text),
    onBackground = colorResource(R.color.glass_text),
)

val DarkMelonColors @Composable get() = darkColors(
    primary = colorResource(R.color.glass_accent),
    primaryVariant = colorResource(R.color.glass_background),
    secondary = colorResource(R.color.glass_accent),
    secondaryVariant = colorResource(R.color.glass_accent),
    background = colorResource(R.color.glass_background),
    surface = colorResource(R.color.glass_surface),
    onPrimary = colorResource(R.color.glass_on_accent),
    onSecondary = colorResource(R.color.glass_on_accent),
    onSurface = colorResource(R.color.glass_text),
    onBackground = colorResource(R.color.glass_text),
)
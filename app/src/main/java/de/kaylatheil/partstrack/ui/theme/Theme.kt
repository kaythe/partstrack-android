/*
 * partstrack - a small inventory for electronic parts
 * Copyright (C) 2026 Kayla Theil (accounts@kaylatheil.de)
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.kaylatheil.partstrack.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Without dynamic colour (before Android 12), the colours follow the orange
// of partstrack's logo (#EA580C).
private val LightColors = lightColorScheme(
    primary = Color(0xFFA33E00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCE),
    onPrimaryContainer = Color(0xFF370E00),
    secondary = Color(0xFF77574B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBCE),
    onSecondaryContainer = Color(0xFF2C160D),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB599),
    onPrimary = Color(0xFF5A1C00),
    primaryContainer = Color(0xFF7F2C00),
    onPrimaryContainer = Color(0xFFFFDBCE),
    secondary = Color(0xFFE7BDAE),
    onSecondary = Color(0xFF442A20),
    secondaryContainer = Color(0xFF5D4035),
    onSecondaryContainer = Color(0xFFFFDBCE),
)

/**
 * The app's Material 3 theme: the device's dynamic colours where available
 * (Android 12 and later), otherwise partstrack's orange; light or dark as the
 * device is set.
 */
@Composable
fun PartstrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

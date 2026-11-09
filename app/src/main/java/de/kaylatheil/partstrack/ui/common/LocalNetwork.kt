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

// From Android 17 on, an app needs the runtime permission ACCESS_LOCAL_NETWORK
// to reach devices on the local network, such as a partstrack server at home
// or in a lab (and the emulator's 10.0.2.2). Without it, connections aren't
// refused but silently dropped, so they only time out: the app has to ask
// up front, and say so when it can't reach the server.

package de.kaylatheil.partstrack.ui.common

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/** Manifest.permission.ACCESS_LOCAL_NETWORK, which exists from Android 17 (API 37) on. */
const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

private const val ANDROID_17 = 37

/** Whether the app may reach the local network: always before Android 17, with the permission after. */
fun localNetworkAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < ANDROID_17 ||
        ContextCompat.checkSelfPermission(context, ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

/** [localNetworkAllowed], checked again whenever the screen shows (it may have been changed in the settings). */
@Composable
fun rememberLocalNetworkAllowed(): Boolean {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(localNetworkAllowed(context)) }
    LifecycleResumeEffect(Unit) {
        allowed = localNetworkAllowed(context)
        onPauseOrDispose {}
    }
    return allowed
}

/**
 * Returns a function that asks for the local network permission if the app
 * doesn't have it, and then runs its argument, whatever the answer (without
 * it, what follows fails and says why).
 */
@Composable
fun rememberLocalNetworkRequest(): (then: () -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending?.invoke()
        pending = null
    }
    return { then ->
        if (localNetworkAllowed(context)) {
            then()
        } else {
            pending = then
            launcher.launch(ACCESS_LOCAL_NETWORK)
        }
    }
}

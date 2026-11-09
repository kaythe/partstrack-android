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

package de.kaylatheil.partstrack.ui.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/** How a scan ended. */
sealed interface ScanOutcome {
    data class Code(val text: String) : ScanOutcome

    data object Cancelled : ScanOutcome

    /** The camera may not be used: scanning needs the permission. */
    data object NoCamera : ScanOutcome
}

/**
 * Returns a function that scans one QR code with zxing's scanner, asking for
 * the camera first if needed, and reports how it ended to [onResult].
 */
@Composable
fun rememberQrScanner(prompt: String, onResult: (ScanOutcome) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val options = ScanOptions()
        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        .setPrompt(prompt)
        .setBeepEnabled(false)
        .setOrientationLocked(false)
        .setCaptureActivity(ScannerActivity::class.java)
    val scan = rememberLauncherForActivityResult(ScanContract()) { result ->
        currentOnResult(result.contents?.let { ScanOutcome.Code(it) } ?: ScanOutcome.Cancelled)
    }
    val askForCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scan.launch(options) else currentOnResult(ScanOutcome.NoCamera)
    }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            scan.launch(options)
        } else {
            askForCamera.launch(Manifest.permission.CAMERA)
        }
    }
}

/** Opens the app's page in the system settings, where a refused permission can be given after all. */
fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private val qrScanner: ImageVector by lazy {
    materialIcon(name = "Filled.QrScanner") {
        // The corners of the viewfinder
        materialPath {
            moveTo(3f, 3f); horizontalLineTo(9f); verticalLineTo(5f); horizontalLineTo(5f); verticalLineTo(9f); horizontalLineTo(3f); close()
            moveTo(15f, 3f); horizontalLineTo(21f); verticalLineTo(9f); horizontalLineTo(19f); verticalLineTo(5f); horizontalLineTo(15f); close()
            moveTo(3f, 15f); horizontalLineTo(5f); verticalLineTo(19f); horizontalLineTo(9f); verticalLineTo(21f); horizontalLineTo(3f); close()
            moveTo(19f, 15f); horizontalLineTo(21f); verticalLineTo(21f); horizontalLineTo(15f); verticalLineTo(19f); horizontalLineTo(19f); close()
        }
        // and a code in it
        materialPath {
            moveTo(7f, 7f); horizontalLineTo(11f); verticalLineTo(11f); horizontalLineTo(7f); close()
            moveTo(13f, 7f); horizontalLineTo(17f); verticalLineTo(11f); horizontalLineTo(13f); close()
            moveTo(7f, 13f); horizontalLineTo(11f); verticalLineTo(17f); horizontalLineTo(7f); close()
            moveTo(13f, 13f); horizontalLineTo(15f); verticalLineTo(15f); horizontalLineTo(13f); close()
            moveTo(15f, 15f); horizontalLineTo(17f); verticalLineTo(17f); horizontalLineTo(15f); close()
        }
    }
}

/** A viewfinder around a QR code, for "scan". (material-icons-core has none.) */
val Icons.Filled.QrScanner: ImageVector get() = qrScanner

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

package de.kaylatheil.partstrack.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import de.kaylatheil.partstrack.AppContainer
import de.kaylatheil.partstrack.data.settings.Settings
import de.kaylatheil.partstrack.ui.parts.PartsScreen
import de.kaylatheil.partstrack.ui.setup.SetupScreen
import de.kaylatheil.partstrack.ui.setup.SetupViewModel
import kotlinx.serialization.Serializable

@Serializable
object PartsRoute

/** Setup on [firstStart] (nothing to go back to), otherwise the settings. */
@Serializable
data class SetupRoute(val firstStart: Boolean = false)

@Composable
fun PartstrackNavHost(container: AppContainer, settings: Settings) {
    val navController = rememberNavController()
    // Chosen once: later changes to the settings must not reset the navigation.
    val start: Any = remember { if (settings.serverUrl == null) SetupRoute(firstStart = true) else PartsRoute }
    NavHost(navController, startDestination = start) {
        composable<SetupRoute> { entry ->
            val route = entry.toRoute<SetupRoute>()
            SetupScreen(
                viewModel = viewModel { SetupViewModel(container.settingsStore, container.apiFactory) },
                firstStart = route.firstStart,
                onDone = {
                    if (route.firstStart) {
                        navController.navigate(PartsRoute) { popUpTo<SetupRoute> { inclusive = true } }
                    } else {
                        navController.popBackStack()
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<PartsRoute> {
            PartsScreen(settings, onOpenSettings = { navController.navigate(SetupRoute()) })
        }
    }
}

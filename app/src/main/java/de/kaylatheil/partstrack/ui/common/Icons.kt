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

package de.kaylatheil.partstrack.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

// Icons that material-icons-core lacks, drawn as Material draws them (the
// extended set is a large dependency for a few icons).

private val remove: ImageVector by lazy {
    materialIcon(name = "Filled.Remove") {
        materialPath {
            moveTo(19.0f, 13.0f)
            horizontalLineTo(5.0f)
            verticalLineToRelative(-2.0f)
            horizontalLineToRelative(14.0f)
            verticalLineToRelative(2.0f)
            close()
        }
    }
}

/** A minus sign, the counterpart of Icons.Filled.Add. */
val Icons.Filled.Remove: ImageVector get() = remove

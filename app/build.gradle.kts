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

import java.util.Properties

// Kotlin support is built into the Android Gradle Plugin (9.0 and later),
// so only the compiler plugins for Compose and kotlinx.serialization are applied here.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/** Where the release key is and how to open it. */
data class ReleaseKey(val storeFile: String, val storePassword: String, val keyAlias: String, val keyPassword: String)

/**
 * The release key, from keystore.properties in the project's root (never committed) or else
 * from the environment variables PARTSTRACK_KEYSTORE, PARTSTRACK_KEYSTORE_PASSWORD and,
 * optionally, PARTSTRACK_KEY_ALIAS (default "partstrack") and PARTSTRACK_KEY_PASSWORD
 * (default the keystore's). Without either, the release APK is built unsigned.
 */
val releaseKey: ReleaseKey? = run {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) {
        val properties = Properties().apply { file.inputStream().use { load(it) } }
        fun property(name: String) = properties.getProperty(name)?.takeIf { it.isNotBlank() }
            ?: error("keystore.properties: $name is missing")
        ReleaseKey(property("storeFile"), property("storePassword"), property("keyAlias"), property("keyPassword"))
    } else {
        fun env(name: String) = providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
        val store = env("PARTSTRACK_KEYSTORE")
        val password = env("PARTSTRACK_KEYSTORE_PASSWORD")
        if (store == null || password == null) {
            null
        } else {
            ReleaseKey(store, password, env("PARTSTRACK_KEY_ALIAS") ?: "partstrack", env("PARTSTRACK_KEY_PASSWORD") ?: password)
        }
    }
}

android {
    namespace = "de.kaylatheil.partstrack"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.kaylatheil.partstrack"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKey != null) {
            create("release") {
                storeFile = file(releaseKey.storeFile)
                storePassword = releaseKey.storePassword
                keyAlias = releaseKey.keyAlias
                keyPassword = releaseKey.keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // The dependency list AGP would add to the APK is encrypted with a key of Google's, which
    // F-Droid doesn't accept; docs/SBOM.md lists the dependencies instead.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true // for the version in the user agent
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // QR codes: zxing-android-embedded (Apache-2.0) works without Google Play Services.
    implementation(libs.zxing.android.embedded)
    implementation(libs.zxing.core)
    implementation(libs.okhttp)
    implementation(libs.okhttp.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

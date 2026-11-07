import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val keystorePropertiesFile = rootProject.file("keystore/keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}


android {
    namespace = "com.adlen.sonora.engine"
    compileSdk = 37

    // Two channels that install side by side: betas ("2.0.0-beta.3") stay "com.adlen.sonora",
    // signed with the first key, as "Sonora Beta"; releases are "com.adlen.sonora.stable",
    // signed with their own key, as "Sonora".
    // Local builds (no version given) are on the beta side, like the phone test builds.
    val version = (findProperty("appVersionName") as String?) ?: "2.3.0"
    // "-beta" as in UpdateChecker.isBeta, one predicate for the app id, the label and the log stripping.
    val beta = findProperty("appVersionName") == null || version.contains("-beta")
    defaultConfig {
        applicationId = "com.adlen.sonora"
        manifestPlaceholders["appLabel"] = "Sonora"
        // The adb command line (com.adlen.sonora.app.cli): betas and debug builds only.
        manifestPlaceholders["cliEnabled"] = "false"
        minSdk = 29
        targetSdk = 36
        // CI release builds pass the tag's version (-PappVersionName / -PappVersionCode).
        versionCode = (findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 200
        versionName = version
        // GitHub "owner/repo" whose releases the app checks for updates; empty = off
        // (local builds). CI sets it to the repository it builds.
        buildConfigField("String", "UPDATE_REPO", "\"${(findProperty("updateRepo") as String?) ?: System.getenv("SONORA_UPDATE_REPO") ?: System.getenv("MYSPOTI_UPDATE_REPO") ?: ""}\"")
        ndk {
            // -Pabis=arm64-v8a: a smaller test APK for one phone.
            abiFilters += (findProperty("abis") as String?)?.split(",") ?: listOf("arm64-v8a", "armeabi-v7a")
        }
        // On-device tests (codecs): ./gradlew connectedDebugAndroidTest
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Release key from the CI secrets (env), else keystore/keystore.properties.
    val envStore = System.getenv("SONORA_KEYSTORE") ?: System.getenv("MYSPOTI_KEYSTORE")
    val hasReleaseKey = envStore != null || keystorePropertiesFile.exists()
    // A stable release (a version without "-beta", given by CI) is never signed with the debug key:
    // without the release key the build stops. Local builds (no version) and -PoverDev are not stable releases.
    val stableRelease = !beta && findProperty("overDev") == null
    if (stableRelease && !hasReleaseKey && gradle.startParameter.taskNames.any { Regex("(assemble|bundle|package|install)Release").containsMatchIn(it) })
        throw GradleException("A stable release needs the release key: set SONORA_KEYSTORE (CI) or add keystore/keystore.properties.")
    // Two artifacts from one code base: the phone app (which Android Auto projects) and the
    // Android Automotive OS app (no launcher, the car draws the media UI).
    flavorDimensions += "device"
    productFlavors {
        create("mobile") {
            dimension = "device"
            isDefault = true
        }
        create("automotive") {
            dimension = "device"
            applicationIdSuffix = ".car"
        }
    }

    signingConfigs {
        create("release") {
            if (envStore != null) {
                storeFile = file(envStore)
                storePassword = System.getenv("SONORA_KEYSTORE_PASSWORD") ?: System.getenv("MYSPOTI_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SONORA_KEY_ALIAS") ?: System.getenv("MYSPOTI_KEY_ALIAS")
                keyPassword = System.getenv("SONORA_KEY_PASSWORD") ?: System.getenv("MYSPOTI_KEY_PASSWORD")
            } else if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file("keystore/${keystoreProperties.getProperty("storeFile")}")
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Installs next to the release apps (own data, own sign-in).
            applicationIdSuffix = ".dev"
            manifestPlaceholders["appLabel"] = "Sonora dev"
            manifestPlaceholders["cliEnabled"] = "true"
        }
        release {
            // Real key when provided (CI releases); otherwise the debug key, so a local
            // release build installs over the debug one while testing.
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
            // -PoverDev: the optimised build under the dev app's id, to measure the speed of a
            // beta on the test phone without replacing the signed beta installed there.
            if (findProperty("overDev") != null) {
                applicationIdSuffix = ".dev"
                manifestPlaceholders["appLabel"] = "Sonora dev"
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Betas are release builds too, but their log reports read Log.v/d/i.
            if (!beta) proguardFiles("proguard-strip-logs.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":host-android"))
    // Third-party extensions: the sandbox (Rhino), manifests, packages and catalogues.
    implementation(project(":extensions"))
    // Jam: scan the invite QR code (Google code scanner, no camera permission).
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    testImplementation("junit:junit:4.13.2")
    // The real org.json in unit tests (android.jar only has stubs).
    testImplementation("org.json:json:20240303")
    // A real XML pull parser in unit tests (the TTML lyrics parser; the tests skip themselves without it).
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    // The Android Auto contract test binds to the media browser service.
    androidTestImplementation(libs.androidx.media)
    // Compose UI tests (the phone flows): the semantics tree, the test activity, JUnit 4.
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.media)


    // Jetpack Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.material)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Native UI (ported from Spotui, GPL-3.0)
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("com.github.bumptech.glide:compose:1.0.0-beta01")
    implementation("androidx.palette:palette-ktx:1.0.0")
    // Frosted (blurred) bars of the Immersive look. Apache-2.0.
    implementation("dev.chrisbanes.haze:haze:1.3.1")
    // MediaTransferReceiver: lets the system output switcher transfer to our Connect routes
    implementation("androidx.mediarouter:mediarouter:1.8.1")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation(libs.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.encoding)
    implementation(libs.ktor.serialization.json)
    // Looping Spotify Canvas video on the now-playing screen
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.8.0")
    // Installs the Baseline Profiles shipped by Compose & co. on sideloaded builds
    // (faster start, less jank; Play does this itself for store installs).
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    // YouTube music videos (search + streams), as in Spotui / NewPipe.
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    implementation(libs.okhttp)
    implementation(libs.security.crypto)
    implementation("androidx.media3:media3-ui:1.8.0")
    implementation("androidx.media3:media3-datasource:1.8.0")
    implementation("androidx.media3:media3-database:1.8.0")
    // Downloads keep going in the background (and after a restart) under WorkManager.
    implementation("androidx.work:work-runtime-ktx:2.12.0")





}
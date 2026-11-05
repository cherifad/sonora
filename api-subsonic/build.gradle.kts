// A Subsonic/OpenSubsonic backend for the Myspoti API contract, under GPL-3.0.
// This is the porting spike: it implements Home -> album -> playback and stubs the rest.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.myspoti.subsonic"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":api"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation(libs.okhttp)
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-common:1.8.0")

    testImplementation("junit:junit:4.13.2")
    // The real org.json on the JVM (the Android stub throws in unit tests).
    testImplementation("org.json:json:20240303")
}

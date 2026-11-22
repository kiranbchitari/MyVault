import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Debug-only escape hatch so screens can be captured while developing:
//   ./gradlew installDebug -Pmyvault.allowScreenshots=true
// Release builds always block screenshots and screen recording.
val allowScreenshots = (findProperty("myvault.allowScreenshots") as String?)?.toBoolean() ?: false

// Slim build for one known phone (the Galaxy S24 Ultra, which launched on Android 14):
//   ./gradlew :app:assembleRelease -Pmyvault.slim=true
// Requires Android 14+, ships only 64-bit ARM code and only English resources. The default build
// keeps the app's full range: Android 10+, every CPU type.
val slim = (findProperty("myvault.slim") as String?)?.toBoolean() ?: false

// Release signing: keystore.properties in the project root (git-ignored) names the keystore
// (storeFile, keyAlias). Without it, release builds are signed with this machine's debug key.
// The password comes from, in order: storePassword in keystore.properties, the
// MYVAULT_KEYSTORE_PASSWORD environment variable, or the macOS Keychain item
// "myvault-release-keystore" that scripts/create-release-key.sh creates.
val keystoreProps = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("keystore.properties")).asText.orNull
        ?.let { load(it.reader()) }
}
val keystorePassword: String? = keystoreProps.getProperty("storePassword")
    ?: providers.environmentVariable("MYVAULT_KEYSTORE_PASSWORD").orNull
    ?: if (keystoreProps.isNotEmpty() && System.getProperty("os.name").startsWith("Mac")) {
        providers.exec {
            commandLine("security", "find-generic-password", "-s", "myvault-release-keystore", "-w")
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim().ifEmpty { null }
    } else {
        null
    }

android {
    namespace = "com.myvault.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.myvault.app"
        minSdk = if (slim) 34 else 29
        targetSdk = 36
        versionCode = 2
        versionName = "0.9.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        if (slim) ndk { abiFilters += "arm64-v8a" }
    }

    if (slim) {
        // MyVault's own text is English; this drops the libraries' translations for ~90 languages.
        androidResources { localeFilters += "en" }
        // Dependency metadata is only read by Google Play.
        dependenciesInfo {
            includeInApk = false
            includeInBundle = false
        }
    }

    signingConfigs {
        // Once keystore.properties exists, release builds never fall back to the debug key: an APK
        // signed with a different key cannot update the installed app. A missing password fails
        // the release build instead.
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile").replaceFirst("~", System.getProperty("user.home")))
                storePassword = keystorePassword
                keyAlias = keystoreProps.getProperty("keyAlias")
                // PKCS12 keystores use one password for the store and the key.
                keyPassword = keystoreProps.getProperty("keyPassword") ?: keystorePassword
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "ALLOW_SCREENSHOTS", allowScreenshots.toString())
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("boolean", "ALLOW_SCREENSHOTS", "false")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        // Exported Room schemas, so migration tests can build any historical database version.
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            "META-INF/{AL2.0,LGPL2.1}",
            // Never read at runtime: Bouncy Castle message bundles for its X.509 path reviewer
            // (only Argon2 and HKDF are used), Kotlin reflection metadata (no kotlin-reflect), and
            // the coroutines debugger agent.
            "org/bouncycastle/**/*.properties",
            "kotlin/**",
            "DebugProbesKt.bin",
            "META-INF/*.version",
        )
    }

}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":crypto"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.extended)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.viewmodel.compose)

    implementation(libs.androidx.biometric)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Optional Google Drive backup: background uploads and OAuth authorization.
    implementation(libs.androidx.work.runtime)
    implementation(libs.play.services.auth)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Empty host activity for rendering individual screens in instrumented tests (debug only).
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.room.testing)
    // Compose UI tests pull in an older Espresso that fails on API 36+; pin the current one.
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

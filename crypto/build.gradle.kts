import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM module: the cryptographic core has no Android dependencies, so it cannot
// reach android.util.Log, Context or storage, and its tests run on the host JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(libs.bouncycastle)
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    maxHeapSize = "1g"
}

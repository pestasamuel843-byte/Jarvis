import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URL
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val sherpaAar = layout.projectDirectory.file("libs/sherpa-onnx-2.0.1.aar")
val downloadSherpaRuntime by tasks.registering {
    outputs.file(sherpaAar)
    doLast {
        val target = sherpaAar.asFile
        if (!target.exists() || target.length() < 10_000_000L) {
            target.parentFile.mkdirs()
            val temp = target.resolveSibling(target.name + ".part")
            URL("https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-2.0.1.aar")
                .openStream().use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
            val digest = MessageDigest.getInstance("SHA-256").digest(temp.readBytes()).joinToString("") { "%02x".format(it) }
            check(digest == "a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7") { "Sherpa-ONNX AAR checksum mismatch" }
            check(temp.renameTo(target)) { "Could not install verified Sherpa-ONNX AAR" }
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(downloadSherpaRuntime) }

android {
    namespace = "com.jarvis.assistant"
    compileSdk = 35
    defaultConfig { applicationId = "com.jarvis.assistant"; minSdk = 26; targetSdk = 35; versionCode = 5; versionName = "1.4.0" }
    buildTypes { release { isMinifyEnabled = false; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation(files("libs/sherpa-onnx-2.0.1.aar"))
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.19.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.HttpURLConnection
import java.net.URL

val offlineModelName = "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm"
val offlineModelUrl = "https://huggingface.co/litert-community/Qwen3-1.7B/resolve/main/$offlineModelName"
val offlineModelAssets = layout.buildDirectory.dir("generated/jarvisOfflineAssets")

val downloadJarvisOfflineModel by tasks.registering {
    val outputModel = offlineModelAssets.map { it.file(offlineModelName) }
    outputs.file(outputModel)
    doLast {
        val destination = outputModel.get().asFile
        if (destination.exists() && destination.length() > 850L * 1024L * 1024L) {
            logger.lifecycle("JARVIS offline model already downloaded.")
        } else {
            destination.parentFile.mkdirs()
            val temporary = File(destination.parentFile, offlineModelName + ".part")
            val connection = (URL(offlineModelUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 180_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "JARVIS-Android-Build")
                setRequestProperty("Accept-Encoding", "identity")
            }
            try {
                connection.connect()
                check(connection.responseCode in 200..299) { "Could not download bundled Qwen3 model: HTTP " + connection.responseCode }
                connection.inputStream.use { input ->
                    temporary.outputStream().buffered().use { output -> input.copyTo(output, 1024 * 1024) }
                }
                check(temporary.length() > 850L * 1024L * 1024L) { "Downloaded Qwen3 model is incomplete (" + temporary.length() + " bytes)." }
                if (destination.exists()) destination.delete()
                check(temporary.renameTo(destination)) { "Could not move bundled Qwen3 model into assets." }
                logger.lifecycle("Bundled Qwen3 model: " + destination.length() + " bytes")
            } finally {
                connection.disconnect()
                temporary.delete()
            }
        }
    }
}


plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.jarvis.assistant"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.jarvis.assistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.3.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    androidResources { noCompress += "litertlm" }
    sourceSets.getByName("main").assets.srcDir(offlineModelAssets)
}

tasks.named("preBuild").configure { dependsOn(downloadJarvisOfflineModel) }

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
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

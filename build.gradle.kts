plugins {
    id("com.android.application") version "8.7.3" apply false
    // LiteRT-LM 0.19.0 brings Kotlin 2.4.x metadata; keep compiler and Compose plugin aligned.
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}

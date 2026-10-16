// Build-only plugins; the app has no runtime dependencies (docs/decisoes/0002).
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.jvm") version "2.4.21" apply false
}

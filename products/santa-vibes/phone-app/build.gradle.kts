// Build-only plugins; the app has no runtime dependencies (same rule as the tv-app, ADR 0002).
plugins {
    id("com.android.application") version "9.4.1" apply false
}

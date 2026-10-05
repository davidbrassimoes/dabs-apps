// Pure Kotlin/JVM host logic shared by every renderer host (no Android, no UI):
// scene model, JSON, shader composition; drift and palette variation in step 0.6.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    // Tests read the shared fixtures (authoring/test/fixtures) and the real scenes.
    systemProperty("santavibes.repo", rootDir.parentFile.absolutePath)
}

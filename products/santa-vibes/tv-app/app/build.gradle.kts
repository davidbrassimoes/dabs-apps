// Android TV app: fullscreen GLES 3.0 renderer. No runtime dependencies (docs/decisoes/0002).
import javax.inject.Inject

plugins {
    id("com.android.application")
}

android {
    namespace = "dev.santavibes.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.santavibes.tv"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.4"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug") // sideload only, for now
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
}

/** Copies scenes/ and shared/ (the single source of truth) into generated assets. */
abstract class CopySceneAssets : DefaultTask() {
    @get:InputDirectory abstract val scenes: DirectoryProperty
    @get:InputDirectory abstract val shared: DirectoryProperty
    @get:OutputDirectory abstract val output: DirectoryProperty
    @get:Inject abstract val fs: FileSystemOperations

    @TaskAction
    fun copy() {
        fs.sync {
            from(scenes) { into("scenes") }
            from(shared) { into("shared") }
            into(output)
        }
    }
}

val copySceneAssets = tasks.register<CopySceneAssets>("copySceneAssets") {
    val repo = rootDir.parentFile
    scenes.set(File(repo, "scenes"))
    shared.set(File(repo, "shared"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copySceneAssets, CopySceneAssets::output)
    }
}

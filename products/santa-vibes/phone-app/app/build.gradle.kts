// Phone sender: captures mic or playback audio, analyses it with signals-core (Rust, via
// JNI) and sends SIGN packets to the TV. No runtime dependencies.
import javax.inject.Inject

plugins {
    id("com.android.application")
}

android {
    namespace = "dev.santavibes.phone"
    compileSdk = 36
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "dev.santavibes.phone"
        minSdk = 29 // AudioPlaybackCapture
        targetSdk = 36
        versionCode = 1
        versionName = "1.4"
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

/**
 * Builds phone-app/native (signals-core + JNI) with cargo-ndk into a generated jniLibs
 * directory. Needs Rust (rustup), the Android targets and cargo-ndk: see phone-app/README.md.
 */
abstract class CargoNdk : DefaultTask() {
    @get:InputDirectory abstract val crate: DirectoryProperty
    @get:InputDirectory abstract val core: DirectoryProperty
    @get:Input abstract val ndkDir: Property<String>
    @get:Input abstract val abis: ListProperty<String>
    @get:OutputDirectory abstract val output: DirectoryProperty
    @get:Inject abstract val exec: ExecOperations

    @TaskAction
    fun build() {
        val cargoBin = File(System.getProperty("user.home"), ".cargo/bin")
        exec.exec {
            workingDir = crate.get().asFile
            environment("ANDROID_NDK_HOME", ndkDir.get())
            environment("PATH", cargoBin.absolutePath + File.pathSeparator + System.getenv("PATH"))
            // The Gradle daemon may not have ~/.cargo/bin on its PATH: prefer the rustup install.
            val exe = if (System.getProperty("os.name").startsWith("Windows")) "cargo.exe" else "cargo"
            val cargo = File(cargoBin, exe).takeIf { it.exists() }?.absolutePath ?: "cargo"
            val args = mutableListOf(cargo, "ndk", "-P", "29", "-o", output.get().asFile.absolutePath)
            abis.get().forEach { args += listOf("-t", it) }
            args += listOf("build", "--release")
            commandLine(args)
        }
    }
}

val cargoNdk = tasks.register<CargoNdk>("cargoNdk") {
    val repo = rootDir.parentFile
    crate.set(File(rootDir, "native"))
    core.set(File(repo, "signals-core"))
    ndkDir.set(androidComponents.sdkComponents.ndkDirectory.map { it.asFile.absolutePath })
    abis.set(listOf("arm64-v8a", "armeabi-v7a", "x86_64"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(cargoNdk, CargoNdk::output)
    }
}

import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose)
}

// Account backend (Supabase). Values come from the environment (CI) or an untracked
// backend.properties; when absent the app is built without accounts and says so in the UI.
val backendFile = rootProject.file("backend.properties")
val backendProps = Properties().apply { if (backendFile.exists()) backendFile.inputStream().use { load(it) } }
fun backendValue(key: String): String =
    (System.getenv("MARGARETHA_$key") ?: backendProps.getProperty(key) ?: "").trim().replace("\\", "").replace("\"", "")

// Release signing: a real keystore when provided through the environment, otherwise the debug key.
val releaseStore = System.getenv("MARGARETHA_KEYSTORE_FILE")?.takeIf { it.isNotBlank() && file(it).exists() }

android {
    namespace = "id.haseda.margaretha"
    compileSdk = 36

    defaultConfig {
        applicationId = "id.haseda.margaretha"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "2.1.0-studio"
        buildConfigField("String", "SUPABASE_URL", "\"${backendValue("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${backendValue("SUPABASE_ANON_KEY")}\"")
        // On-device speech recognition (whisper.cpp) is built for 64-bit ARM phones only.
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake { arguments += listOf("-DANDROID_STL=c++_static", "-DWHISPER_DIR=${layout.buildDirectory.get().asFile}/whisper-src/whisper.cpp-1.7.6") } }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    androidResources {
        // The speech model is memory-copied once on first use; keep it uncompressed in the APK.
        noCompress += "bin"
    }

    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = System.getenv("MARGARETHA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MARGARETHA_KEY_ALIAS")
                keyPassword = System.getenv("MARGARETHA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Without a provided keystore the release is signed with the debug key so the APK still
            // installs directly; such a build cannot be uploaded to Play.
            signingConfig = signingConfigs.getByName(if (releaseStore != null) "release" else "debug")
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

    lint {
        // Lint still runs and reports in CI (lintDebug); it does not block the private APK build.
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":core:timeline"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.media3.common)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)

    testImplementation(libs.junit4)
}

// Speech model for auto subtitles: downloaded once at build time, never committed to git.
val whisperModel = layout.projectDirectory.file("src/main/assets/whisper/ggml-base-q5_1.bin").asFile
val downloadWhisperModel by tasks.registering {
    val target = whisperModel
    outputs.file(target)
    doLast {
        if (target.exists() && target.length() > 10_000_000) return@doLast
        target.parentFile.mkdirs()
        val tmp = File(target.parentFile, target.name + ".part")
        URI("https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin").toURL().openStream().use { input ->
            tmp.outputStream().use { input.copyTo(it) }
        }
        check(tmp.length() > 10_000_000) { "Whisper model download looks incomplete" }
        tmp.renameTo(target)
    }
}
val downloadWhisperSource by tasks.registering {
    val dir = layout.buildDirectory.dir("whisper-src").get().asFile
    outputs.dir(dir)
    doLast {
        if (File(dir, "whisper.cpp-1.7.6/CMakeLists.txt").exists()) return@doLast
        dir.mkdirs()
        val tar = File(dir, "whisper.tar.gz")
        URI("https://github.com/ggml-org/whisper.cpp/archive/refs/tags/v1.7.6.tar.gz").toURL().openStream().use { input ->
            tar.outputStream().use { input.copyTo(it) }
        }
        val proc = ProcessBuilder("tar", "-xzf", tar.absolutePath, "-C", dir.absolutePath).inheritIO().start()
        check(proc.waitFor() == 0) { "whisper.cpp source extract failed" }
    }
}
tasks.named("preBuild") { dependsOn(downloadWhisperModel, downloadWhisperSource) }

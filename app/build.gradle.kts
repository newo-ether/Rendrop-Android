plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.newoether.rendrop"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.newoether.rendrop"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"


        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.compose.material.icons.extended)

    // Kotlinx serialization
    implementation(libs.kotlinx.serialization.json)

    // OkHttp
    implementation(libs.okhttp)

    // Coil
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Splash screen
    implementation(libs.androidx.core.splashscreen)

    // Material
    implementation(libs.material)

    // DataStore
    implementation(libs.datastore.preferences)

    // FFmpeg
    implementation(libs.ffmpeg.kit.min)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Animation
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.animation.core)
    // Seed-color Material scheme generation (same as Agora)
    implementation(libs.material.color.utilities)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.ui.tooling)
}

// Source size gate: every handwritten Kotlin file stays at or below 800 physical lines.
// CRLF, LF and a lone CR each count as one line break, so Windows and Linux agree.
val maxKotlinFileLines = 800
val verifyKotlinFileSize by tasks.registering {
    group = "verification"
    description = "Fails when a Kotlin source file exceeds $maxKotlinFileLines physical lines."
    val sources = fileTree("src") { include("**/*.kt", "**/*.kts") }
    inputs.files(sources)
    doLast {
        val oversized = sources.files.mapNotNull { file ->
            val text = file.readText()
            val breaks = Regex("\r\n|\r|\n").findAll(text).count()
            val lines = if (text.isEmpty()) 0 else breaks + if (text.endsWith("\n") || text.endsWith("\r")) 0 else 1
            if (lines > maxKotlinFileLines) "${file.relativeTo(projectDir)}: $lines lines" else null
        }.sorted()
        if (oversized.isNotEmpty()) {
            throw GradleException(
                "Kotlin source files over $maxKotlinFileLines lines:\n" + oversized.joinToString("\n")
            )
        }
        logger.lifecycle("verifyKotlinFileSize: ${sources.files.size} files, limit $maxKotlinFileLines")
    }
}
tasks.named("preBuild") { dependsOn(verifyKotlinFileSize) }

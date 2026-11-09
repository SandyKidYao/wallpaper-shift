import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.wallpapershift.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wallpapershift.app"
        // ImageDecoder (EXIF-aware, HEIF-capable decoding) needs API 28.
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    // Release signing material stays outside version control, in the repo's .local/release-signing/
    // (keystore.properties: storeFile, storePassword, keyAlias, keyPassword). Without it the release
    // build comes out unsigned.
    val signingDir = rootDir.resolve("../.local/release-signing")
    val signingProps = signingDir.resolve("keystore.properties").takeIf { it.isFile }
        ?.let { file -> Properties().apply { file.inputStream().use(::load) } }
    if (signingProps != null) {
        signingConfigs {
            create("release") {
                storeFile = signingDir.resolve(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
        }
        release {
            if (signingProps != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The demo set's pictures live in the repo's shared/ folder so every platform's build can use them.
    // The folder is an asset root: its demo/ directory becomes assets/demo/ in every build (the app
    // creates the demo set on first launch and copies the pictures into the photo library).
    sourceSets.getByName("main").assets.srcDir(rootDir.resolve("../shared/demo-assets"))

    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    lint {
        // Versions are pinned to a known-good, locally cached baseline.
        disable += setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable", "OldTargetApi")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val keystoreProps = Properties().also { props ->
    rootProject.file("keystore.properties").takeIf { it.exists() }
        ?.inputStream()?.use { props.load(it) }
}

// Dedicated Play upload-key keystore, kept separate from the GitHub-distribution one
// above so a compromise of one channel's signing key never affects the other. This
// file does not exist until the Play upload keystore is created — see CLAUDE.md
// "Play release rules". Falls back to the GitHub keystore so local builds
// (assemblePlayDebug/assemblePlayRelease) still work before that key exists; swap in
// the real play-keystore.properties before ever uploading a playRelease build to Play
// Console — never ship a play release actually signed with the GitHub key.
val playKeystoreProps = Properties().also { props ->
    rootProject.file("play-keystore.properties").takeIf { it.exists() }
        ?.inputStream()?.use { props.load(it) }
}

val localProps = Properties().also { props ->
    rootProject.file("local.properties").takeIf { it.exists() }
        ?.inputStream()?.use { props.load(it) }
}

android {
    namespace = "com.saiyanstrong"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.saiyanstrong"
        minSdk = 26
        targetSdk = 35
        versionCode = 81
        versionName = "0.59.0"

        buildConfigField("String", "SUPABASE_URL", "\"${localProps["supabase.url"] ?: ""}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProps["supabase.anonKey"] ?: ""}\"")
        buildConfigField("String", "SUPABASE_GOOGLE_WEB_CLIENT_ID", "\"${localProps["supabase.googleWebClientId"] ?: ""}\"")
    }

    flavorDimensions += "distribution"
    productFlavors {
        // Sideloaded builds: in-app GitHub-releases updater is active.
        create("github") {
            dimension = "distribution"
            buildConfigField("String", "DISTRIBUTION", "\"github\"")
        }
        // Play Store builds: self-update UI is fully hidden (Play policy forbids
        // self-updating apps) — Play's own update mechanism takes over instead.
        // Same applicationId and versionCode/versionName stream as github — this is
        // the same app on two channels, not two separate installs.
        create("play") {
            dimension = "distribution"
            buildConfigField("String", "DISTRIBUTION", "\"play\"")
        }
    }

    signingConfigs {
        create("release") {
            storeFile     = file(keystoreProps["storeFile"] as? String ?: "saiyanstrong.keystore")
            storePassword = keystoreProps["storePassword"] as? String ?: "saiyanstrong"
            keyAlias      = keystoreProps["keyAlias"]      as? String ?: "saiyanstrong"
            keyPassword   = keystoreProps["keyPassword"]   as? String ?: "saiyanstrong"
        }
        create("playRelease") {
            val hasPlayKeystore = playKeystoreProps["storeFile"] != null
            storeFile = file(
                playKeystoreProps["storeFile"] as? String
                    ?: (keystoreProps["storeFile"] as? String ?: "saiyanstrong.keystore")
            )
            storePassword = (if (hasPlayKeystore) playKeystoreProps["storePassword"] as? String else null)
                ?: keystoreProps["storePassword"] as? String ?: "saiyanstrong"
            keyAlias = (if (hasPlayKeystore) playKeystoreProps["keyAlias"] as? String else null)
                ?: keystoreProps["keyAlias"] as? String ?: "saiyanstrong"
            keyPassword = (if (hasPlayKeystore) playKeystoreProps["keyPassword"] as? String else null)
                ?: keystoreProps["keyPassword"] as? String ?: "saiyanstrong"
        }
    }

    productFlavors.getByName("play").signingConfig = signingConfigs.getByName("playRelease")

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            signingConfig  = signingConfigs.getByName("release")
            isMinifyEnabled = true
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

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.lottie.compose)
    implementation(libs.coil.compose)

    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler.work)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.video)
    implementation(libs.camerax.view)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    testImplementation(libs.junit)
}

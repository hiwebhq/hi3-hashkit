plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "hi3.hashkit.wear"
    compileSdk = 35

    defaultConfig {
        // Must match the phone app's applicationId so the Wearable Data Layer pairs them.
        applicationId = "hi3.hashkit"
        minSdk = 26
        targetSdk = 34
        versionCode = 53
        versionName = "0.48.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Wear-specific UI.
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)

    // Wear Tiles + ProtoLayout for the glanceable fleet tile.
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material)
    implementation(libs.androidx.concurrent.futures)

    // Wearable Data Layer, to receive the fleet summary from the phone.
    implementation(libs.play.services.wearable)

    implementation(libs.kotlinx.coroutines.android)
}

import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
}

// Static analysis: existing findings are frozen in detekt-baseline.xml; only NEW issues
// fail the build (CI runs `detekt`). Refresh the baseline with `./gradlew detektBaseline`.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("detekt.yml"))
    baseline = file("detekt-baseline.xml")
    // Main sources only — tests may exceed style thresholds (fixtures, param-heavy helpers).
    source.setFrom(files("src/main/kotlin"))
}

// Release signing. The keystore and its passwords live OUTSIDE the project tree so a
// workstation backup/sync of the repo folder can never carry them: first $HI3_KEYSTORE_PROPERTIES,
// then ~/.config/hi3/keystore.properties, then (legacy) the repo root. `storeFile` is resolved
// relative to the properties file. Keep both files mode 600; never commit either.
val keystorePropsFile: File? = listOfNotNull(
    System.getenv("HI3_KEYSTORE_PROPERTIES")?.let { File(it) },
    File(System.getProperty("user.home"), ".config/hi3/keystore.properties"),
    rootProject.file("keystore.properties"),
).firstOrNull { it.isFile }
val keystoreProps = Properties().apply {
    keystorePropsFile?.inputStream()?.use { load(it) }
}

android {
    namespace = "hi3.hashkit"
    compileSdk = 37

    defaultConfig {
        applicationId = "hi3.hashkit"
        minSdk = 26
        targetSdk = 37
        versionCode = 149
        versionName = "0.83.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Hidden dev link (7 logo taps). On in debug; the release/store build turns it off.
        buildConfigField("boolean", "EASTER_EGG", "true")

        // In-app self-update (download + install a signed APK) is for SIDELOAD builds only —
        // Google Play forbids apps updating themselves. Build the Play AAB with -PplayStore
        // to compile it out. On by default so normal/sideload builds include it.
        buildConfigField("boolean", "SELF_UPDATE", (!project.hasProperty("playStore")).toString())
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = File(keystoreProps.getProperty("storeFile")).let { f ->
                    if (f.isAbsolute) f else File(keystorePropsFile!!.parentFile, f.path)
                }
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
            // Store build ships without the hidden dev link.
            buildConfigField("boolean", "EASTER_EGG", "false")
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true // Robolectric needs merged resources
        }
    }
}

// Release integrity: write app-release.apk.sha256 next to the APK so the checksum can be
// published as a release asset (`./gradlew releaseChecksum`).
tasks.register("releaseChecksum") {
    dependsOn("assembleRelease")
    val apk = layout.buildDirectory.file("outputs/apk/release/app-release.apk")
    val out = layout.buildDirectory.file("outputs/apk/release/app-release.apk.sha256")
    outputs.file(out)
    doLast {
        val bytes = apk.get().asFile.readBytes()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val hex = digest.joinToString("") { b -> "%02x".format(b) }
        out.get().asFile.writeText("$hex  ${apk.get().asFile.name}\n")
        println("SHA-256 $hex  ${apk.get().asFile.name}")
    }
}

// Unit tests run on the debug variant only; the release unit-test variant is redundant
// and its resource merge trips Robolectric Compose tests.
androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        (variant as? com.android.build.api.variant.HasUnitTestBuilder)?.enableUnitTest = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // Per-app language: AppCompatDelegate.setApplicationLocales backport for API 26-32.
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    debugImplementation(libs.okhttp.logging)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // Offline QR scanning for payout-address entry (no Google dependency, on-device).
    implementation(libs.zxing.android.embedded)

    // Wearable Data Layer: publish a small fleet summary to a paired Wear OS tile.
    implementation(libs.play.services.wearable)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

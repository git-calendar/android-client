plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// The app version comes from git tags ("1.2.3"), so no file holds it:
// - the release workflow passes the pushed tag as -Papp.version.number;
// - every other build asks `git describe`: "1.2.3" on a tagged commit, "1.2.3-4-gabc1234" four commits
//   after it, so a local or CI build shows which commit it came from;
// - without git or tags (a shallow clone, a source zip) the version is 0.0.0-dev.
val appVersion: String = providers.gradleProperty("app.version.number").orNull
    ?: runCatching {
        providers.exec {
            commandLine("git", "describe", "--tags", "--match", "[0-9]*.[0-9]*.[0-9]*")
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim()
    }.getOrNull()?.takeIf { it.isNotEmpty() }
    ?: "0.0.0-dev"

android {
    namespace = "dev.jotalac"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.jotalac"
        minSdk = 31
        targetSdk = 37

        // version name "1.2.3" (or "1.2.3-4-gabc1234" between releases), version code 10203
        val (major, minor, patch) = Regex("""^(\d+)\.(\d+)\.(\d+)""").find(appVersion)
            ?.destructured?.toList()?.map { it.toInt() }
            ?: error("The version '$appVersion' does not start with major.minor.patch")
        require(minor < 100 && patch < 100) { "minor and patch must be smaller then 100" }

        // Android requires a version code of at least 1, also for 0.0.0-dev builds
        versionCode = (major * 10_000 + minor * 100 + patch).coerceAtLeast(1)
        versionName = appVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing for CI: the release workflow writes the keystore from the `release` environment's
    // secrets and passes it in these variables. Without them (local builds) the release build stays
    // unsigned, and Android Studio's "Generate Signed App Bundle or APK" signs it as before.
    val keystoreFile = providers.environmentVariable("ANDROID_KEYSTORE_FILE").orNull
    if (keystoreFile != null) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // R8 code and resource shrinking; the default Android rules are included.
            optimization {
                enable = true
                keepRules {
                    files.add(file("proguard-rules.pro"))
                }
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        generateLocaleConfig = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // downloaded from git-calendar/core releases with `make fetch-core`
    implementation(files("libs/core.aar"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

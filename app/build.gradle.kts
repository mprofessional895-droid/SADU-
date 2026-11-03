import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The gguf engine needs the NDK to build llama.cpp. When no NDK is installed the app still builds
// and ships, it just keeps the on-device engine it already had - see LlamaEngine.isAvailable().
val ndkRoot = file(
    "${System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT") ?: "${System.getProperty("user.home")}/android-sdk"}/ndk"
)
val hasNdk = ndkRoot.isDirectory && !(ndkRoot.list()?.isEmpty() ?: true)

// Use the NDK that is actually installed instead of pinning one exact version: CI images rotate
// their NDK, and asking for a version the runner does not have makes the SDK installer download a
// ~650 MB archive, which has been failing there. Preference order first, then newest available.
val installedNdks = ndkRoot.listFiles()
    ?.filter { it.isDirectory && File(it, "source.properties").exists() }
    ?.map { it.name }
    .orEmpty()
val preferredNdks = listOf("26.1.10909125", "27.3.13750724", "27.2.12479018", "28.2.13676358")
val ndkVersionToUse = preferredNdks.firstOrNull { it in installedNdks }
    ?: installedNdks.maxWithOrNull(compareBy { it.substringBefore('.').toIntOrNull() ?: 0 })

// One certificate for every development build, whoever builds it.
//
// Android refuses to install an APK over one signed with a different key, which is why a build from
// a different machine used to need an uninstall first. These values come from `keystore.properties`
// (git-ignored) or the environment, so the key itself never enters the repository. With neither
// present the build falls back to Gradle's throwaway debug key and still works.
val signingProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(env: String, key: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: signingProps.getProperty(key)?.takeIf { it.isNotBlank() }

val signStorePath = signingValue("SADU_KEYSTORE", "storeFile")
val signStorePassword = signingValue("SADU_STORE_PASSWORD", "storePassword")
val signKeyAlias = signingValue("SADU_KEY_ALIAS", "keyAlias")
val signKeyPassword = signingValue("SADU_KEY_PASSWORD", "keyPassword")
val signFile = signStorePath?.let { rootProject.file(it) }?.takeIf { it.isFile }

android {
    namespace = "com.sadu.assistant"
    compileSdk = 34

    if (hasNdk && ndkVersionToUse != null) {
        ndkVersion = ndkVersionToUse
    }

    defaultConfig {
        applicationId = "com.sadu.assistant"
        minSdk = 26
        targetSdk = 34
        versionCode = 45
        versionName = "0.36.2"
        ndk { abiFilters += listOf("arm64-v8a") }

        if (hasNdk) {
            externalNativeBuild {
                cmake {
                    // -PsaduArm=armv8-a builds a library that also runs on pre-2018 phones.
                    val arm = (project.findProperty("saduArm") as String?) ?: "armv8.2-a+dotprod+fp16"
                    arguments += listOf(
                        "-DANDROID_STL=c++_shared",
                        "-DCMAKE_BUILD_TYPE=Release",
                        "-DsaduArmBaseline=$arm",
                    )
                }
            }
        }
    }

    if (hasNdk) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }

    signingConfigs {
        if (signFile != null) {
            create("sadu") {
                storeFile = signFile
                storePassword = signStorePassword
                keyAlias = signKeyAlias
                keyPassword = signKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            if (signFile != null) signingConfig = signingConfigs.getByName("sadu")
        }
        release {
            isMinifyEnabled = false
            if (signFile != null) signingConfig = signingConfigs.getByName("sadu")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

    testOptions {
        unitTests {
            // Robolectric needs the app's resources to render Compose off-device
            isIncludeAndroidResources = true
            all { it.systemProperty("roborazzi.test.record", "true") }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Runs Gemma on the phone, fully offline
    implementation("com.google.mediapipe:tasks-genai:0.10.27")

    testImplementation("junit:junit:4.13.2")
    // A real org.json on the test classpath: the phone jar stubs it out.
    testImplementation("org.json:json:20240303")

    // Renders the real Compose UI to PNG on the JVM, so the design can be checked
    // without a device or emulator.
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.11.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.11.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

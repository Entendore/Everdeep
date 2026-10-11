plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.focusrealm.everdeep"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.focusrealm.everdeep"
        minSdk = 26; targetSdk = 34
        versionCode = 2; versionName = "1.1.0"
        resourceConfigurations += listOf("en")
    }
    val ksProps = rootProject.file("keystore.properties")
    if (ksProps.exists()) {
        val p = java.util.Properties().apply { ksProps.inputStream().use { load(it) } }
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(p["storeFile"] as String)
                storePassword = p["storePassword"] as String
                keyAlias = p["keyAlias"] as String
                keyPassword = p["keyPassword"] as String
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true; isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (ksProps.exists()) signingConfig = signingConfigs.getByName("release")
        }
        debug { applicationIdSuffix = ".debug" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = false; disable += "MissingTranslation" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nickapp.chatcifrata"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nickapp.chatcifrata"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1-prototipo"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")

    // Sicurezza: storage locale cifrato per salvare le chiavi private
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Coroutine: usate dal livello di rete per gestire connessioni senza bloccare l'interfaccia
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // QR code: generazione (ZXing core) e scansione tramite una fotocamera integrata nell'app,
    // nessuna app esterna richiesta. Libreria open source, matura e molto diffusa.
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}

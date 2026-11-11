plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "fr.papertv.eink"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.papertv.eink"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.3"
    }

    // Deux variantes à partir du même code (comme Paper Meteo) :
    //  - eink  : Mudita Kompakt et autres écrans E Ink, noir et blanc, page par page ;
    //  - color : téléphones à écran couleur, thème chaud, défilement fluide.
    // Elles peuvent être installées côte à côte.
    flavorDimensions += "display"
    productFlavors {
        create("eink") {
            dimension = "display"
            applicationId = "fr.papertv.eink"
            buildConfigField("boolean", "COLOR", "false")
        }
        create("color") {
            dimension = "display"
            applicationId = "fr.papertv.color"
            buildConfigField("boolean", "COLOR", "true")
        }
    }

    // Clé de signature fixe, versionnée comme dans Paper Meteo : chaque nouvelle version
    // s'installe par-dessus l'ancienne. PAPERTV_KEYSTORE permet d'en utiliser une autre.
    signingConfigs {
        create("release") {
            storeFile = System.getenv("PAPERTV_KEYSTORE")?.let(::file) ?: file("papertv.keystore")
            storePassword = System.getenv("PAPERTV_STORE_PASSWORD") ?: "papertv"
            keyAlias = System.getenv("PAPERTV_KEY_ALIAS") ?: "papertv"
            keyPassword = System.getenv("PAPERTV_KEY_PASSWORD") ?: "papertv"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
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
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation(composeBom)
    testImplementation("junit:junit:4.13.2")
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.39.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.39.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Fichiers XMLTV réels pour les captures d'écran (facultatif).
tasks.withType<Test>().configureEach {
    listOf("papertv.tnt", "papertv.fr", "papertv.today", "papertv.placeholder").forEach { key ->
        project.findProperty(key)?.let { systemProperty(key, it.toString()) }
    }
    maxHeapSize = "3g"
}

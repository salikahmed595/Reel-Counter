plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

import java.util.Properties

/** Values come from ~/.gradle/gradle.properties, the project gradle.properties, or -P flags — never hardcoded secrets. */
fun prop(name: String, default: String = ""): String = (project.findProperty(name) as String?)?.trim() ?: default

// Release signing (upload key) from keystore.properties — kept out of version control.
val keystoreProps = Properties().apply {
    // Prefer a path outside the project (set reelcounter.keystoreProperties in ~/.gradle/gradle.properties).
    val f = prop("reelcounter.keystoreProperties").takeIf { it.isNotEmpty() }?.let { file(it) } ?: rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.reelcounter.app"
    compileSdk = 37

    defaultConfig {
        // PAICONS Self-Development. Permanent once published (Play) or once users install it (website).
        applicationId = prop("reelcounter.applicationId", "com.paicons.selfdevelopment")
        minSdk = 29
        targetSdk = 37
        versionCode = prop("reelcounter.versionCode", "1").toInt()
        versionName = prop("reelcounter.versionName", "1.0.0")

        // Billing verification backend (PRD §11). Empty = verification unavailable, Pro stays locked.
        buildConfigField("String", "BILLING_BACKEND_URL", "\"${prop("reelcounter.billingBackendUrl")}\"")
        // Public HTTPS privacy policy (required by Google Play for this app).
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"${prop("reelcounter.privacyPolicyUrl")}\"")
        buildConfigField("String", "SUPPORT_EMAIL", "\"${prop("reelcounter.supportEmail")}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
            buildConfigField("boolean", "DIAGNOSTICS", "false")
        }
        debug {
            buildConfigField("boolean", "DIAGNOSTICS", "true")
        }
        // Same R8/minify as release, debug-signed, with structural diagnostics logging. For pre-release QA only.
        create("staging") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "DIAGNOSTICS", "true")
        }
    }

    // Two distributions of the same app:
    //  - play:    Google Play build with Play Billing subscriptions (upload the AAB).
    //  - website: APK downloaded from paicons.com. Play Billing can't sell to sideloaded installs,
    //             so purchase screens are hidden; everything else is identical.
    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "BILLING_ENABLED", "true")
            buildConfigField("String", "DISTRIBUTION", "\"play\"")
        }
        create("website") {
            dimension = "distribution"
            buildConfigField("boolean", "BILLING_ENABLED", "false")
            buildConfigField("String", "DISTRIBUTION", "\"website\"")
        }
    }

    // In-app English/Urdu switching needs every language in the base APK, not Play language splits.
    bundle {
        language { enableSplit = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.work.runtime.ktx)
    implementation(libs.billing.ktx)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}

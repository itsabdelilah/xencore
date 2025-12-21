import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

android {
    namespace = "io.ads.mediation"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        // targetSdk removed - deprecated for library modules in AGP 9.0+

        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    buildFeatures {
        viewBinding = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    // Core Android (exposed via api)
    api(libs.androidx.core.ktx)

    // Ad SDKs (exposed to apps via 'api')
    api(libs.google.mobile.ads)
    api(libs.ump.sdk)
    api(libs.meta.adapter)
    api(libs.applovin.adapter)
    api(libs.lifecycle.process)

    // Firebase Remote Config (exposed via 'api')
    api(platform("com.google.firebase:firebase-bom:34.7.0"))
    api(libs.firebase.config)

    // Shimmer (for optional native ad loading effect)
    api(libs.shimmer)

    // Billing (exposed via api for IAP)
    api(libs.billing.ktx)
    implementation(libs.fragment.ktx)
}

// Publishing configuration for JitPack
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.contabox"
                artifactId = "ads-mediation"
                version = "1.1.3"
            }
        }
    }
}

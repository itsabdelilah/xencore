package io.ads.demo

import android.app.Application
import com.google.firebase.FirebaseApp
import io.ads.mediation.AdsManager

/**
 * Demo Application class showing how to use the AdMob Mediation library.
 *
 * This demonstrates the initialization flow:
 * 1. Initialize Firebase (required for Remote Config)
 * 2. Initialize AdsManager (handles all ad setup)
 *
 * In your own app, create a similar Application class and initialize the library the same way.
 */
class DemoApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // STEP 1: Initialize Firebase (required for Remote Config)
        FirebaseApp.initializeApp(this)

        // STEP 2: Initialize AdsManager (handles all ad initialization)
        // This will:
        // - Initialize and fetch Remote Config synchronously
        // - Attempt App ID override if configured
        // - Set up consent management
        AdsManager.init(this)
    }
}

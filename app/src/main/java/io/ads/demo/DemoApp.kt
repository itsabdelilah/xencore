package io.ads.demo

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import io.ads.mediation.AdConfig
import io.ads.mediation.AdsManager

/**
 * Demo Application class for XenCore library integration.
 *
 * This demonstrates proper initialization with 3-tier fallback system:
 * 1. Firebase Remote Config (best) - from your Firebase Console
 * 2. AdConfig defaults (good) - hardcoded in this file
 * 3. Library test IDs (last resort) - from XenCore library
 */
class DemoApp : Application() {

    companion object {
        private const val TAG = "DemoApp"
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "═════════════════════════════════════")
        Log.d(TAG, "🚀 DemoApp Starting - XenCore Integration Demo")
        Log.d(TAG, "═════════════════════════════════════")

        try {
            // STEP 1: Initialize Firebase
            // This connects to YOUR Firebase project (google-services.json)
            Log.d(TAG, "Initializing Firebase...")
            FirebaseApp.initializeApp(this)
            Log.d(TAG, "✅ Firebase initialized")

            // STEP 2: Create app-specific default ad units
            // These will be used if Firebase Remote Config fails (no internet, timeout, etc.)
            // Replace with YOUR actual ad unit IDs for production!
            Log.d(TAG, "Creating app-specific AdConfig...")
            val adConfig = AdConfig(
                appOpenAdUnit = "ca-app-pub-3940256099942544/9257395921",       // Demo's App Open (test ID for demo)
                interstitialAdUnit = "ca-app-pub-3940256099942544/1033173712",  // Demo's Interstitial (test ID for demo)
                rewardedAdUnit = "ca-app-pub-3940256099942544/5224354917",      // Demo's Rewarded (test ID for demo)
                nativeAdUnit = "ca-app-pub-3940256099942544/2247696110",        // Demo's Native (test ID for demo)
                adFrequencySeconds = 30L
            )

            // STEP 3: Initialize AdsManager WITH app defaults
            // This enables 3-tier fallback: Firebase → AdConfig → Library test IDs
            Log.d(TAG, "Initializing AdsManager with AdConfig...")
            AdsManager.init(this, adConfig)
            Log.d(TAG, "✅ AdsManager initialized with app-specific defaults")

            Log.d(TAG, "═════════════════════════════════════")
            Log.d(TAG, "✅ DemoApp Initialization Complete")
            Log.d(TAG, "📊 Fallback Chain:")
            Log.d(TAG, "   1. Firebase Remote Config (will try to fetch)")
            Log.d(TAG, "   2. DemoApp's AdConfig (if Firebase fails)")
            Log.d(TAG, "   3. Library test IDs (emergency fallback)")
            Log.d(TAG, "   Next: Call requestConsentAndInitialize() in Activity")
            Log.d(TAG, "═════════════════════════════════════")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Initialization failed", e)
            Log.e(TAG, "   App may not function correctly")
        }
    }
}

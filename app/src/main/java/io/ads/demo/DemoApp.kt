package io.ads.demo

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import io.ads.mediation.AdConfig
import io.ads.mediation.AdsManager
import io.ads.mediation.billing.PremiumFeaturesManager
import io.ads.mediation.billing.SubscriptionManager

/**
 * Demo Application class for AdMediation library integration.
 *
 * This demonstrates the new non-blocking initialization pattern:
 * - Quick init in Application.onCreate() (no blocking fetch)
 * - Complete init with fetch in SplashActivity (with UI feedback)
 *
 * Benefits:
 * - No 5-second freeze on app launch
 * - Immediate UI feedback in splash screen
 * - Same 3-tier fallback system as before
 */
class DemoApp : Application() {

    companion object {
        private const val TAG = "DemoApp"
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "═════════════════════════════════════")
        Log.d(TAG, "⚡ DemoApp Starting - Non-blocking Init")
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

            // STEP 3: Initialize Billing Managers
            Log.d(TAG, "Initializing billing managers...")
            PremiumFeaturesManager.init(this)
            SubscriptionManager.init(this, productId = "instantballx")
            Log.d(TAG, "✅ Billing managers initialized")

            // STEP 4: Quick initialization WITHOUT blocking fetch
            // The fetch and App ID override are deferred to SplashActivity
            Log.d(TAG, "Quick AdsManager init (no blocking fetch)...")
            AdsManager.initWithoutFetch(this, adConfig)

            // STEP 5: Set up premium checker for ad blocking
            AdsManager.premiumChecker = { PremiumFeaturesManager.hasPremiumAccess() }
            Log.d(TAG, "✅ Quick init complete - no blocking!")

            Log.d(TAG, "═════════════════════════════════════")
            Log.d(TAG, "✅ DemoApp Quick Init Complete")
            Log.d(TAG, "📊 What happens next:")
            Log.d(TAG, "   1. SplashActivity shows immediately")
            Log.d(TAG, "   2. Fetch Remote Config during splash")
            Log.d(TAG, "   3. Override App ID if configured")
            Log.d(TAG, "   4. Initialize consent and SDK")
            Log.d(TAG, "   5. Navigate to MainActivity when ready")
            Log.d(TAG, "═════════════════════════════════════")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Initialization failed", e)
            Log.e(TAG, "   App may not function correctly")
        }
    }
}

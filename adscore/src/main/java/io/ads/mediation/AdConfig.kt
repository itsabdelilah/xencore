package io.ads.mediation

/**
 * Configuration for app-specific default ad unit IDs.
 *
 * This class allows each app using XenCore to provide their own fallback ad units
 * that will be used when Firebase Remote Config is unavailable (no internet, timeout, error).
 *
 * ## 3-Tier Fallback Priority:
 * ```
 * 1. Firebase Remote Config (best)     → Your remote values from Firebase Console
 * 2. AdConfig (good)                    → Your app's hardcoded defaults (this class)
 * 3. Library test IDs (last resort)     → Google's test ad units (AdUnits.kt)
 * ```
 *
 * ## Why This Matters:
 * Without AdConfig, when Firebase fails, ALL apps using XenCore would show the same
 * fallback ads (from AdUnits.kt). With AdConfig, each app has its own unique fallbacks.
 *
 * ## Usage Example:
 * ```kotlin
 * class MyApp : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *
 *         FirebaseApp.initializeApp(this)
 *
 *         // Provide your app's default ad units
 *         val config = AdConfig(
 *             appOpenAdUnit = "ca-app-pub-XXXXX/1111111111",
 *             interstitialAdUnit = "ca-app-pub-XXXXX/2222222222",
 *             rewardedAdUnit = "ca-app-pub-XXXXX/3333333333",
 *             nativeAdUnit = "ca-app-pub-XXXXX/4444444444",
 *             adFrequencySeconds = 30L
 *         )
 *
 *         AdsManager.init(this, config)
 *     }
 * }
 * ```
 *
 * ## When Firebase Works:
 * Your Remote Config values are used (AdConfig is ignored).
 *
 * ## When Firebase Fails:
 * Your AdConfig values are used (not the library's test IDs).
 *
 * ## When Both Fail (Firebase + AdConfig not provided):
 * Library falls back to Google's test ad units (AdUnits.kt).
 *
 * @property appOpenAdUnit Your App Open ad unit ID
 * @property interstitialAdUnit Your Interstitial ad unit ID
 * @property rewardedAdUnit Your Rewarded ad unit ID
 * @property nativeAdUnit Your Native ad unit ID
 * @property adFrequencySeconds Minimum seconds between showing interstitial/app open ads
 *
 * @see AdsManager.init
 * @see RemoteConfigManager
 * @see AdUnits
 */
data class AdConfig(
    val appOpenAdUnit: String,
    val interstitialAdUnit: String,
    val rewardedAdUnit: String,
    val nativeAdUnit: String,
    val adFrequencySeconds: Long = 30L
) {
    init {
        require(appOpenAdUnit.isNotBlank()) { "appOpenAdUnit cannot be blank" }
        require(interstitialAdUnit.isNotBlank()) { "interstitialAdUnit cannot be blank" }
        require(rewardedAdUnit.isNotBlank()) { "rewardedAdUnit cannot be blank" }
        require(nativeAdUnit.isNotBlank()) { "nativeAdUnit cannot be blank" }
        require(adFrequencySeconds > 0) { "adFrequencySeconds must be positive" }
    }
}

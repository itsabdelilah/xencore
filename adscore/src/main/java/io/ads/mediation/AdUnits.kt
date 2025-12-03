package io.ads.mediation

/**
 * Last-resort fallback ad unit IDs using Google's official test IDs.
 *
 * ## 3-Tier Fallback Priority:
 * ```
 * 1. Firebase Remote Config (best)     → Your remote values from Firebase Console
 * 2. AdConfig (good)                    → App-provided defaults via AdsManager.init()
 * 3. AdUnits (last resort)              → Google's test IDs (THIS FILE)
 * ```
 *
 * ## When These Are Used:
 * - Firebase Remote Config fails to fetch
 * - AND app didn't provide AdConfig to AdsManager.init()
 *
 * ## Why Test IDs:
 * These are Google's official test ad units that work on all devices and don't
 * generate real revenue. This ensures that if both Firebase and AdConfig fail,
 * the app shows test ads instead of someone else's production ads.
 *
 * ## Production Apps Should:
 * 1. Configure Remote Config in Firebase Console (best)
 * 2. Provide AdConfig to AdsManager.init() (good fallback)
 * 3. Never rely on these test IDs in production (emergency only)
 *
 * @see AdConfig
 * @see RemoteConfigManager
 */
object AdUnits {
    /**
     * Google's official test App Open ad unit ID.
     * Works on all devices, shows test ads only.
     */
    const val OPEN = "ca-app-pub-3940256099942544/9257395921"

    /**
     * Google's official test Interstitial ad unit ID.
     * Works on all devices, shows test ads only.
     */
    const val INTER = "ca-app-pub-3940256099942544/1033173712"

    /**
     * Google's official test Rewarded ad unit ID.
     * Works on all devices, shows test ads only.
     */
    const val REWARD = "ca-app-pub-3940256099942544/5224354917"

    /**
     * Google's official test Native ad unit ID.
     * Works on all devices, shows test ads only.
     */
    const val NATIVE = "ca-app-pub-3940256099942544/2247696110"

    /**
     * Default frequency capping interval in seconds.
     * Controls minimum time between showing interstitial and app open ads.
     */
    const val FREQUENCY_SECONDS = 30L
}

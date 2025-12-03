package io.ads.mediation

/**
 * Central location for all AdMob ad unit IDs and frequency settings.
 *
 * These serve as **FALLBACK DEFAULTS** when Firebase Remote Config is unavailable.
 * In production, values are fetched from Remote Config via RemoteConfigManager.
 */
object AdUnits {
    // Production ad unit IDs (fallback defaults)
    const val OPEN = "ca-app-pub-5141318425870910/1716271883"
    const val INTER = "ca-app-pub-5141318425870910/1025458002"
    const val REWARD = "ca-app-pub-5141318425870910/6313992441"
    const val NATIVE = "ca-app-pub-5141318425870910/4135033546"

    // Frequency capping interval in seconds (fallback default)
    // Controls minimum time between showing interstitial and app open ads
    const val FREQUENCY_SECONDS = 30L
}

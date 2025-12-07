package io.ads.mediation

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

/**
 * Singleton manager for Firebase Remote Config.
 *
 * ⚠️ IMPORTANT: This library does NOT include google-services.json
 *
 * ## Architecture Overview
 *
 * **Library provides:**
 * - ✅ Firebase Remote Config CODE (this file)
 * - ✅ Firebase SDK dependency (exposed via 'api')
 * - ❌ NO google-services.json file
 * - ❌ NO Firebase project configuration
 *
 * **Each app must provide:**
 * 1. Their own google-services.json file (in app/ module)
 * 2. Apply google-services plugin in app/build.gradle.kts
 * 3. Initialize Firebase in their Application class
 * 4. Configure Remote Config parameters in their Firebase Console
 *
 * ## How It Works at Runtime
 *
 * When you call `FirebaseRemoteConfig.getInstance()`, it automatically uses
 * whatever Firebase project the app initialized with via google-services.json.
 *
 * **Example Flow:**
 * ```
 * App: FFH Quatro
 *   ├─ app/google-services.json (FFH's Firebase project)
 *   ├─ FirebaseApp.initializeApp(this) → connects to FFH's project
 *   ├─ AdsManager.init(this) → calls RemoteConfigManager.init()
 *   └─ FirebaseRemoteConfig.getInstance() → uses FFH's project automatically!
 * ```
 *
 * **Multi-App Example:**
 * - **FFH Quatro app** uses FFH's google-services.json → gets FFH's Remote Config
 * - **Sensi Max app** uses Sensi's google-services.json → gets Sensi's Remote Config
 * - **Same library code** works for both!
 *
 * ## What This Manages
 *
 * - Remote ad unit IDs for all 4 placement types
 * - Runtime AdMob App ID override
 * - Configurable ad frequency capping interval
 * - Graceful fallback to hardcoded defaults if fetch fails
 *
 * @see AdsManager.init
 */
object RemoteConfigManager {

    private const val TAG = "RemoteConfigManager"

    // Remote Config keys
    private const val KEY_ADMOB_APP_ID = "admob_app_id"
    private const val KEY_OPEN_AD_UNIT = "open_ad_unit"
    private const val KEY_INTER_AD_UNIT = "inter_ad_unit"
    private const val KEY_REWARD_AD_UNIT = "reward_ad_unit"
    private const val KEY_NATIVE_AD_UNIT = "native_ad_unit"
    private const val KEY_AD_FREQUENCY_SECONDS = "ad_frequency_seconds"

    private lateinit var remoteConfig: FirebaseRemoteConfig
    private var isInitialized = false
    private var appConfig: AdConfig? = null

    /**
     * Initialize Firebase Remote Config with defaults.
     * Call this once from Application.onCreate()
     *
     * ## 3-Tier Fallback Priority:
     * 1. Firebase Remote Config (fetched from your Firebase project)
     * 2. AdConfig (app-provided defaults via this parameter)
     * 3. AdUnits (library's test IDs - last resort)
     *
     * @param config Optional app-specific default ad units.
     *               Recommended for production apps to ensure proper fallback behavior.
     *
     * @see AdConfig
     */
    fun init(config: AdConfig? = null) {
        if (isInitialized) return

        appConfig = config

        if (config != null) {
            Log.d(TAG, "✅ App-provided AdConfig received:")
            Log.d(TAG, "   ├─ App Open: ${config.appOpenAdUnit}")
            Log.d(TAG, "   ├─ Interstitial: ${config.interstitialAdUnit}")
            Log.d(TAG, "   ├─ Rewarded: ${config.rewardedAdUnit}")
            Log.d(TAG, "   ├─ Native: ${config.nativeAdUnit}")
            Log.d(TAG, "   └─ Frequency: ${config.adFrequencySeconds}s")
        } else {
            Log.w(TAG, "⚠️  No AdConfig provided - will use test IDs if Remote Config fails")
            Log.w(TAG, "   Recommended: Provide AdConfig to AdsManager.init() for production")
        }

        try {
            // Get Remote Config instance
            remoteConfig = FirebaseRemoteConfig.getInstance()

            // Configure settings: 1-hour fetch interval
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600) // 1 hour
                .build()

            remoteConfig.setConfigSettingsAsync(configSettings)

            // Set default values (use app config if provided, otherwise library test IDs)
            val defaults = hashMapOf<String, Any>(
                KEY_ADMOB_APP_ID to "", // Empty = use manifest value
                KEY_OPEN_AD_UNIT to (config?.appOpenAdUnit ?: AdUnits.OPEN),
                KEY_INTER_AD_UNIT to (config?.interstitialAdUnit ?: AdUnits.INTER),
                KEY_REWARD_AD_UNIT to (config?.rewardedAdUnit ?: AdUnits.REWARD),
                KEY_NATIVE_AD_UNIT to (config?.nativeAdUnit ?: AdUnits.NATIVE),
                KEY_AD_FREQUENCY_SECONDS to (config?.adFrequencySeconds ?: AdUnits.FREQUENCY_SECONDS)
            )

            remoteConfig.setDefaultsAsync(defaults)

            isInitialized = true
            Log.d(TAG, "✅ RemoteConfigManager initialized")

            // Fetch fresh values from server
            fetchAndActivate()

        } catch (e: Exception) {
            Log.e(TAG, "❌ RemoteConfig initialization failed: ${e.message}")
            if (config != null) {
                Log.w(TAG, "⚠️ Will use app-provided AdConfig as fallback")
            } else {
                Log.w(TAG, "⚠️ Will use library test IDs as fallback")
            }
        }
    }

    /**
     * Fetch and activate remote config values (asynchronous)
     */
    private fun fetchAndActivate() {
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val updated = task.result
                    Log.d(TAG, "════════════════════════════════════")
                    Log.d(TAG, "🔄 Remote Config fetched ${if (updated) "(NEW VALUES)" else "(CACHED)"}")
                    Log.d(TAG, "════════════════════════════════════")
                    logCurrentConfig()
                } else {
                    Log.e(TAG, "❌ Remote Config fetch failed: ${task.exception?.message}")
                    Log.w(TAG, "⚠️ Using default/cached values")
                    logCurrentConfig()
                }
            }
    }

    /**
     * Fetch and activate remote config values ASYNCHRONOUSLY with callback.
     *
     * This is the async version for use in SplashActivity, providing a callback
     * when the fetch completes (or fails).
     *
     * @param onComplete Callback with success status (true if fetched, false if failed)
     */
    fun fetchAndActivateAsync(onComplete: (Boolean) -> Unit) {
        if (!isInitialized) {
            Log.e(TAG, "❌ Cannot fetch: RemoteConfig not initialized")
            onComplete(false)
            return
        }

        Log.d(TAG, "📡 Starting async fetch...")

        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val updated = task.result
                    Log.i(TAG, "✅ Async fetch SUCCESS ${if (updated) "(NEW VALUES)" else "(CACHED)"}")
                    logCurrentConfig()
                    onComplete(true)
                } else {
                    Log.e(TAG, "❌ Async fetch FAILED: ${task.exception?.message}")
                    Log.w(TAG, "⚠️ Using default/cached values")
                    logCurrentConfig()
                    onComplete(false)
                }
            }
    }

    /**
     * Fetch and activate remote config values SYNCHRONOUSLY with timeout.
     *
     * ⚠️ CRITICAL: This blocks the calling thread!
     * - Use this ONLY during app initialization
     * - Required for App ID override to work (must fetch BEFORE MobileAds.initialize)
     *
     * @param timeoutMs Maximum time to wait for fetch (default: 5000ms)
     * @return true if fetch succeeded, false if failed or timed out
     */
    @Synchronized
    fun fetchAndActivateSync(timeoutMs: Long = 5000): Boolean {
        if (!isInitialized) {
            Log.e(TAG, "❌ Cannot fetch: RemoteConfig not initialized")
            return false
        }

        val startTime = System.currentTimeMillis()
        var fetchSucceeded = false
        var isComplete = false

        Log.d(TAG, "📡 Starting synchronous fetch (timeout: ${timeoutMs}ms)...")

        // Start the async fetch
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                synchronized(this) {
                    fetchSucceeded = task.isSuccessful
                    isComplete = true

                    val duration = System.currentTimeMillis() - startTime

                    if (task.isSuccessful) {
                        val updated = task.result
                        Log.i(TAG, "✅ Sync fetch SUCCESS in ${duration}ms ${if (updated) "(NEW VALUES)" else "(CACHED)"}")
                        logCurrentConfig()
                    } else {
                        Log.e(TAG, "❌ Sync fetch FAILED in ${duration}ms")
                        if (task.exception != null) {
                            Log.e(TAG, "   Exception type: ${task.exception!!.javaClass.simpleName}")
                            Log.e(TAG, "   Error message: ${task.exception!!.message}")
                            task.exception!!.printStackTrace()
                        }
                        Log.w(TAG, "⚠️ Using default/cached values")
                        logCurrentConfig()
                    }

                    (this as Object).notifyAll()
                }
            }

        // Wait for completion or timeout
        synchronized(this) {
            val deadline = startTime + timeoutMs

            while (!isComplete && System.currentTimeMillis() < deadline) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining > 0) {
                    try {
                        (this as Object).wait(remaining)
                    } catch (e: InterruptedException) {
                        Log.w(TAG, "⚠️ Fetch interrupted", e)
                        Thread.currentThread().interrupt()
                        return false
                    }
                }
            }

            if (!isComplete) {
                val duration = System.currentTimeMillis() - startTime
                Log.w(TAG, "⏱️ Sync fetch TIMEOUT after ${duration}ms")
                Log.w(TAG, "⚠️ Using default/cached values")
                logCurrentConfig()
                return false
            }

            return fetchSucceeded
        }
    }

    /**
     * Log current active configuration
     */
    private fun logCurrentConfig() {
        val appId = getAdmobAppId()
        Log.d(TAG, "════════════════════════════════════")
        Log.d(TAG, "📱 Active Remote Config Values:")
        Log.d(TAG, "   ├─ AdMob App ID: '${appId}' ${if (appId.isBlank()) "(empty - will use manifest)" else ""}")
        Log.d(TAG, "   ├─ App Open: ${getOpenAdUnit()}")
        Log.d(TAG, "   ├─ Interstitial: ${getInterAdUnit()}")
        Log.d(TAG, "   ├─ Rewarded: ${getRewardAdUnit()}")
        Log.d(TAG, "   ├─ Native: ${getNativeAdUnit()}")
        Log.d(TAG, "   └─ Ad Frequency: ${getAdFrequencySeconds()}s")

        // Show fetch info
        val lastFetchStatus = remoteConfig.info.lastFetchStatus
        val lastFetchTime = remoteConfig.info.fetchTimeMillis
        Log.d(TAG, "📊 Fetch Info:")
        Log.d(TAG, "   ├─ Last fetch status: $lastFetchStatus")
        Log.d(TAG, "   └─ Last fetch time: ${if (lastFetchTime > 0) "$lastFetchTime ms ago" else "never"}")
        Log.d(TAG, "════════════════════════════════════")
    }

    /**
     * Get AdMob App ID for runtime override
     * @return Remote App ID, or empty string to use manifest value
     */
    fun getAdmobAppId(): String {
        return if (isInitialized) {
            remoteConfig.getString(KEY_ADMOB_APP_ID).trim()
        } else {
            ""
        }
    }

    /**
     * Get App Open ad unit ID
     */
    fun getOpenAdUnit(): String {
        return if (isInitialized) {
            remoteConfig.getString(KEY_OPEN_AD_UNIT).takeIf { it.isNotEmpty() }
                ?: AdUnits.OPEN
        } else {
            AdUnits.OPEN
        }
    }

    /**
     * Get Interstitial ad unit ID
     */
    fun getInterAdUnit(): String {
        return if (isInitialized) {
            remoteConfig.getString(KEY_INTER_AD_UNIT).takeIf { it.isNotEmpty() }
                ?: AdUnits.INTER
        } else {
            AdUnits.INTER
        }
    }

    /**
     * Get Rewarded ad unit ID
     */
    fun getRewardAdUnit(): String {
        return if (isInitialized) {
            remoteConfig.getString(KEY_REWARD_AD_UNIT).takeIf { it.isNotEmpty() }
                ?: AdUnits.REWARD
        } else {
            AdUnits.REWARD
        }
    }

    /**
     * Get Native ad unit ID
     */
    fun getNativeAdUnit(): String {
        return if (isInitialized) {
            remoteConfig.getString(KEY_NATIVE_AD_UNIT).takeIf { it.isNotEmpty() }
                ?: AdUnits.NATIVE
        } else {
            AdUnits.NATIVE
        }
    }

    /**
     * Get ad frequency interval in seconds
     */
    fun getAdFrequencySeconds(): Long {
        return if (isInitialized) {
            remoteConfig.getLong(KEY_AD_FREQUENCY_SECONDS).takeIf { it > 0 }
                ?: AdUnits.FREQUENCY_SECONDS
        } else {
            AdUnits.FREQUENCY_SECONDS
        }
    }
}

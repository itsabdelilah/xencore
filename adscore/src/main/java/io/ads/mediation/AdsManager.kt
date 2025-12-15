package io.ads.mediation

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.util.concurrent.Executors
import com.google.android.gms.ads.*
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.initialization.AdapterStatus
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Singleton AdsManager following Google's best practices.
 * Handles initialization, preloading, showing, and lifecycle management for all ad types.
 *
 * Features:
 * - UMP consent management
 * - Optimized initialization (non-blocking)
 * - App Open, Interstitial, Rewarded, and Native ads
 * - Automatic preloading
 * - Timeout handling
 * - One-line ad calls with callbacks
 */
object AdsManager : DefaultLifecycleObserver {

    private const val TAG = "AdsManager"

    private lateinit var application: Application
    private var isInitialized = false
    private var canRequestAds = false

    // Ad instances
    private var appOpenAd: AppOpenAd? = null
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null

    // Loading states
    private var isLoadingAppOpen = false
    private var isLoadingInterstitial = false
    private var isLoadingRewarded = false
    private var isShowingAd = false

    // Consent
    private lateinit var consentInformation: ConsentInformation

    // Background executor for SDK initialization
    private val initializationExecutor = Executors.newSingleThreadExecutor()

    // Frequency capping (timestamp tracking)
    private var lastInterstitialTime: Long = 0
    private var lastAppOpenTime: Long = 0

    /**
     * Initialize the AdsManager. Call this ONCE in Application.onCreate()
     *
     * CRITICAL INITIALIZATION ORDER:
     * 1. Get current manifest App ID (for logging)
     * 2. Initialize Remote Config (with optional app defaults)
     * 3. Fetch Remote Config SYNCHRONOUSLY (5s timeout)
     * 4. Attempt App ID override (THE HACK - must be before MobileAds.initialize)
     * 5. Set up lifecycle observer and consent
     *
     * ## 3-Tier Fallback System:
     * ```
     * 1. Firebase Remote Config (best)     → Your remote values from Firebase Console
     * 2. AdConfig (good)                    → Your app's hardcoded defaults
     * 3. Library test IDs (last resort)     → Google's test ad units
     * ```
     *
     * ## Usage:
     * ```kotlin
     * // Option 1: With app defaults (RECOMMENDED for production)
     * val config = AdConfig(
     *     appOpenAdUnit = "ca-app-pub-XXXXX/1111111111",
     *     interstitialAdUnit = "ca-app-pub-XXXXX/2222222222",
     *     rewardedAdUnit = "ca-app-pub-XXXXX/3333333333",
     *     nativeAdUnit = "ca-app-pub-XXXXX/4444444444"
     * )
     * AdsManager.init(this, config)
     *
     * // Option 2: Without app defaults (uses test IDs if Firebase fails)
     * AdsManager.init(this)
     * ```
     *
     * @param app Application instance
     * @param adConfig Optional app-specific default ad units. Highly recommended for production.
     *
     * Note: MobileAds.initialize() is called later from requestConsentAndInitialize()
     *
     * @see AdConfig
     * @see RemoteConfigManager
     */
    fun init(app: Application, adConfig: AdConfig? = null) {
        if (isInitialized) {
            Log.w(TAG, "⚠️ Already initialized, skipping")
            return
        }

        application = app

        Log.d(TAG, "════════════════════════════════════")
        Log.d(TAG, "🚀 AdsManager Initialization Starting")
        Log.d(TAG, "════════════════════════════════════")

        // STEP 1: Get current manifest App ID (for logging)
        val manifestAppId = AdmobAppIdOverride.getCurrentAppId(app)
        Log.d(TAG, "📋 Manifest App ID: $manifestAppId")

        // STEP 2: Initialize Remote Config (with app defaults if provided)
        Log.d(TAG, "⚙️  Initializing Remote Config...")
        RemoteConfigManager.init(adConfig)

        // STEP 3: Fetch Remote Config (BLOCKING - critical for App ID override)
        Log.d(TAG, "📡 Fetching Remote Config (blocking, 5s timeout)...")
        val fetchSuccess = RemoteConfigManager.fetchAndActivateSync(timeoutMs = 5000)

        if (fetchSuccess) {
            Log.i(TAG, "✅ Remote Config fetched successfully")
        } else {
            Log.w(TAG, "⚠️  Remote Config fetch failed/timeout - using defaults")
        }

        // STEP 4: Attempt App ID override (THE HACK)
        val remoteAppId = RemoteConfigManager.getAdmobAppId()

        if (remoteAppId.isNotBlank()) {
            Log.d(TAG, "🔧 Attempting App ID override...")
            Log.d(TAG, "   Remote App ID: $remoteAppId")

            val overrideSuccess = AdmobAppIdOverride.override(app, remoteAppId)

            if (overrideSuccess) {
                Log.i(TAG, "✅ App ID override successful - SDK will use: $remoteAppId")
            } else {
                Log.w(TAG, "⚠️  App ID override failed - SDK will use manifest value: $manifestAppId")
            }
        } else {
            Log.d(TAG, "ℹ️  No remote App ID configured - SDK will use manifest value: $manifestAppId")
        }

        // STEP 5: Log frequency setting
        val frequency = RemoteConfigManager.getAdFrequencySeconds()
        Log.d(TAG, "⏱️  Ad frequency capping: ${frequency}s")

        // STEP 6: Set up lifecycle observer for app open ads
        Log.d(TAG, "🔄 Setting up lifecycle observer...")
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        // STEP 7: Initialize consent information
        Log.d(TAG, "🔐 Initializing consent information...")
        consentInformation = UserMessagingPlatform.getConsentInformation(app)

        isInitialized = true

        Log.d(TAG, "✅ AdsManager initialization complete")
        Log.d(TAG, "   Next step: Call requestConsentAndInitialize() from Activity")
        Log.d(TAG, "════════════════════════════════════")
    }

    /**
     * Initialize WITHOUT blocking fetch - for use in Application.onCreate()
     *
     * This method does quick initialization without any blocking operations.
     * The Remote Config fetch and App ID override are deferred to SplashActivity.
     *
     * @param app Application instance
     * @param adConfig Optional app-specific default ad units. Highly recommended for production.
     *
     * @see initializeOnSplash for completing initialization with fetch
     */
    fun initWithoutFetch(app: Application, adConfig: AdConfig? = null) {
        if (isInitialized) {
            Log.w(TAG, "⚠️ Already initialized, skipping")
            return
        }

        application = app

        Log.d(TAG, "════════════════════════════════════")
        Log.d(TAG, "⚡ AdsManager Quick Init (no fetch)")
        Log.d(TAG, "════════════════════════════════════")

        // Get current manifest App ID (for logging)
        val manifestAppId = AdmobAppIdOverride.getCurrentAppId(app)
        Log.d(TAG, "📋 Manifest App ID: $manifestAppId")

        // Initialize Remote Config WITHOUT fetch
        Log.d(TAG, "⚙️  Initializing Remote Config (no fetch)...")
        RemoteConfigManager.init(adConfig)

        // Set up lifecycle observer for app open ads
        Log.d(TAG, "🔄 Setting up lifecycle observer...")
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        // Initialize consent information
        Log.d(TAG, "🔐 Initializing consent information...")
        consentInformation = UserMessagingPlatform.getConsentInformation(app)

        isInitialized = true

        Log.d(TAG, "✅ Quick initialization complete")
        Log.d(TAG, "   Next: Call initializeOnSplash() from SplashActivity")
        Log.d(TAG, "════════════════════════════════════")
    }

    /**
     * Complete initialization on SplashActivity with async operations.
     *
     * This method:
     * 1. Fetches Remote Config asynchronously
     * 2. Attempts App ID override if configured
     * 3. Requests consent
     * 4. Initializes MobileAds SDK
     *
     * All operations are async with progress callbacks for UI updates.
     *
     * @param activity The SplashActivity instance
     * @param onProgress Called with progress messages for UI updates
     * @param onComplete Called when initialization completes (success = true/false)
     */
    fun initializeOnSplash(
        activity: Activity,
        onProgress: (String) -> Unit = {},
        onComplete: (Boolean) -> Unit
    ) {
        if (!isInitialized) {
            Log.e(TAG, "❌ Must call initWithoutFetch() first!")
            onComplete(false)
            return
        }

        Log.d(TAG, "════════════════════════════════════")
        Log.d(TAG, "🚀 Splash Initialization Starting")
        Log.d(TAG, "════════════════════════════════════")

        onProgress("Fetching configuration...")

        // STEP 1: Fetch Remote Config asynchronously
        RemoteConfigManager.fetchAndActivateAsync { fetchSuccess ->
            if (fetchSuccess) {
                Log.i(TAG, "✅ Remote Config fetched successfully")
                onProgress("Configuration loaded")
            } else {
                Log.w(TAG, "⚠️ Remote Config fetch failed - using defaults")
                onProgress("Using default configuration")
            }

            // STEP 2: Attempt App ID override
            val manifestAppId = AdmobAppIdOverride.getCurrentAppId(activity)
            val remoteAppId = RemoteConfigManager.getAdmobAppId()

            if (remoteAppId.isNotBlank()) {
                Log.d(TAG, "🔧 Attempting App ID override...")
                onProgress("Configuring ad network...")

                val overrideSuccess = AdmobAppIdOverride.override(activity, remoteAppId)

                if (overrideSuccess) {
                    Log.i(TAG, "✅ App ID override successful - SDK will use: $remoteAppId")
                } else {
                    Log.w(TAG, "⚠️ App ID override failed - SDK will use manifest value: $manifestAppId")
                }
            }

            // STEP 3: Request consent and initialize SDK
            onProgress("Checking consent...")

            val params = ConsentRequestParameters.Builder().build()

            consentInformation.requestConsentInfoUpdate(
                activity,
                params,
                {
                    // Consent info updated
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                        if (formError != null) {
                            Log.e(TAG, "Consent form error: ${formError.message}")
                        }

                        // Initialize SDK if can request ads
                        if (consentInformation.canRequestAds()) {
                            onProgress("Initializing ad SDK...")
                            initializeMobileAdsSdk()
                            onProgress("Ready")
                            onComplete(true)
                        } else {
                            onComplete(false)
                        }
                    }
                },
                { requestError ->
                    Log.e(TAG, "Consent request error: ${requestError.message}")
                    // Still try to initialize if consent is not required
                    if (consentInformation.canRequestAds()) {
                        onProgress("Initializing ad SDK...")
                        initializeMobileAdsSdk()
                        onProgress("Ready")
                        onComplete(true)
                    } else {
                        onComplete(false)
                    }
                }
            )

            // Check if already have consent
            if (consentInformation.canRequestAds()) {
                onProgress("Initializing ad SDK...")
                initializeMobileAdsSdk()
                // Don't call onComplete here as consent flow might still be running
            }
        }
    }

    /**
     * Request consent and initialize SDK. Call this from your first Activity.
     */
    fun requestConsentAndInitialize(activity: Activity) {
        val params = ConsentRequestParameters.Builder()
            // Uncomment for testing:
            // .setConsentDebugSettings(
            //     ConsentDebugSettings.Builder(activity)
            //         .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
            //         .addTestDeviceHashedId("YOUR_TEST_DEVICE_ID")
            //         .build()
            // )
            .build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                // Consent info updated successfully
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.e(TAG, "Consent form error: ${formError.message}")
                    }
                    // Can request ads
                    if (consentInformation.canRequestAds()) {
                        initializeMobileAdsSdk()
                    }
                }
            },
            { requestError ->
                Log.e(TAG, "Consent request error: ${requestError.message}")
                // Still initialize SDK in case consent is not required
                if (consentInformation.canRequestAds()) {
                    initializeMobileAdsSdk()
                }
            }
        )

        // Check if consent is already obtained
        if (consentInformation.canRequestAds()) {
            initializeMobileAdsSdk()
        }
    }

    /**
     * Initialize Google Mobile Ads SDK on background thread (SDK 24.x requirement)
     */
    private fun initializeMobileAdsSdk() {
        if (canRequestAds) return

        canRequestAds = true

        // Initialize on background thread as required by SDK 24.x
        initializationExecutor.execute {
            MobileAds.initialize(application) { status ->
                Log.d(TAG, "═══════════════════════════════════════")
                Log.d(TAG, "📱 MobileAds SDK initialized on background thread")
                Log.d(TAG, "═══════════════════════════════════════")

                var appLovinFound = false
                var appLovinReady = false
                status.adapterStatusMap.forEach { (adapter, adapterStatus) ->
                    val statusIcon = when (adapterStatus.initializationState) {
                        AdapterStatus.State.READY -> "✅"
                        AdapterStatus.State.NOT_READY -> "⚠️"
                        else -> "❌"
                    }

                    // Detect AppLovin specifically
                    if (adapter.contains("applovin", ignoreCase = true)) {
                        appLovinFound = true
                        appLovinReady = adapterStatus.initializationState == AdapterStatus.State.READY
                        Log.d(TAG, "$statusIcon APPLOVIN DETECTED: $adapter")
                        Log.d(TAG, "   └─ Status: ${adapterStatus.initializationState}")
                        Log.d(TAG, "   └─ Latency: ${adapterStatus.latency}ms")
                        Log.d(TAG, "   └─ Description: ${adapterStatus.description}")
                    } else {
                        Log.d(TAG, "$statusIcon $adapter | ${adapterStatus.initializationState} | ${adapterStatus.latency}ms")
                    }
                }

                if (appLovinFound && appLovinReady) {
                    Log.d(TAG, "🎯 AppLovin mediation adapter is READY and will participate in bidding!")
                } else if (appLovinFound && !appLovinReady) {
                    Log.w(TAG, "⚠️ AppLovin adapter found but NOT READY - Check SDK Key in AdMob mediation settings!")
                    Log.w(TAG, "   → Go to AdMob Console → Mediation → AppLovin → Enter your SDK Key")
                } else {
                    Log.w(TAG, "⚠️ AppLovin adapter not found - check AdMob mediation setup")
                }
                Log.d(TAG, "═══════════════════════════════════════")

                // Preload first interstitial on main thread after SDK init
                Handler(Looper.getMainLooper()).post {
                    preloadInterstitial()
                }
            }
        }
    }

    /**
     * Log detailed ResponseInfo to track which network loaded the ad
     */
    private fun logAdResponseInfo(adType: String, responseInfo: ResponseInfo?) {
        if (responseInfo == null) {
            Log.w(TAG, "⚠️ $adType: No ResponseInfo available")
            return
        }

        val loadedAdapter = responseInfo.loadedAdapterResponseInfo
        val mediationAdapterClassName = loadedAdapter?.adapterClassName ?: "Unknown"

        // Detect network from adapter class name
        val networkName = when {
            mediationAdapterClassName.contains("applovin", ignoreCase = true) -> "🔵 APPLOVIN"
            mediationAdapterClassName.contains("facebook", ignoreCase = true) ||
            mediationAdapterClassName.contains("meta", ignoreCase = true) -> "🔷 META (Facebook)"
            mediationAdapterClassName.contains("google", ignoreCase = true) ||
            mediationAdapterClassName == "Unknown" -> "🟢 ADMOB"
            else -> "❓ $mediationAdapterClassName"
        }

        Log.d(TAG, "════════════════════════════════════")
        Log.d(TAG, "✅ $adType LOADED BY: $networkName")
        Log.d(TAG, "   ├─ Adapter: $mediationAdapterClassName")
        Log.d(TAG, "   ├─ Response ID: ${responseInfo.responseId}")
        Log.d(TAG, "   └─ Latency: ${loadedAdapter?.latencyMillis ?: 0}ms")

        // Log mediation waterfall
        val adapterResponses = responseInfo.adapterResponses
        if (adapterResponses.isNotEmpty()) {
            Log.d(TAG, "📊 Mediation Waterfall (${adapterResponses.size} adapters):")
            adapterResponses.forEachIndexed { index, adapterResponse ->
                val adapterName = adapterResponse.adapterClassName.split(".").lastOrNull() ?: adapterResponse.adapterClassName
                val latency = adapterResponse.latencyMillis
                val error = adapterResponse.adError

                val status = if (error == null) {
                    "✅ Success ($latency ms)"
                } else {
                    "❌ ${error.message} (${error.code})"
                }

                Log.d(TAG, "   ${index + 1}. $adapterName → $status")
            }
        }
        Log.d(TAG, "════════════════════════════════════")
    }

    /**
     * Get frequency capping interval in milliseconds from Remote Config
     */
    private fun getFrequencyMillis(): Long {
        return RemoteConfigManager.getAdFrequencySeconds() * 1000
    }

    /**
     * Get remaining seconds until Interstitial ad is available (0 if available now)
     */
    fun getInterstitialRemainingSeconds(): Long {
        if (lastInterstitialTime == 0L) return 0L

        val currentTime = System.currentTimeMillis()
        val timeSinceLastAd = currentTime - lastInterstitialTime
        val frequencyMillis = getFrequencyMillis()

        if (timeSinceLastAd >= frequencyMillis) return 0L

        return (frequencyMillis - timeSinceLastAd) / 1000
    }

    /**
     * Get remaining seconds until App Open ad is available (0 if available now)
     */
    fun getAppOpenRemainingSeconds(): Long {
        if (lastAppOpenTime == 0L) return 0L

        val currentTime = System.currentTimeMillis()
        val timeSinceLastAd = currentTime - lastAppOpenTime
        val frequencyMillis = getFrequencyMillis()

        if (timeSinceLastAd >= frequencyMillis) return 0L

        return (frequencyMillis - timeSinceLastAd) / 1000
    }

    // ========== APP OPEN AD ==========

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        // Preload app open ad on app resume
        preloadAppOpen()
    }

    /**
     * Preload app open ad
     */
    fun preloadAppOpen() {
        if (!canRequestAds || isLoadingAppOpen || appOpenAd != null) return

        isLoadingAppOpen = true
        val request = AdRequest.Builder().build()

        AppOpenAd.load(
            application,
            RemoteConfigManager.getOpenAdUnit(),
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isLoadingAppOpen = false
                    Log.d(TAG, "App Open Ad loaded")

                    // Log which network loaded the ad
                    logAdResponseInfo("APP OPEN AD", ad.responseInfo)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingAppOpen = false
                    Log.e(TAG, "❌ App Open Ad failed: ${error.message} (Code: ${error.code})")
                    Log.e(TAG, "   └─ Domain: ${error.domain}")
                }
            }
        )
    }

    /**
     * Show app open ad with timeout and frequency capping
     */
    fun showAppOpen(
        activity: Activity,
        timeoutMs: Long = 1000,
        onComplete: () -> Unit,
        onFail: () -> Unit
    ) {
        if (!canRequestAds || isShowingAd) {
            onFail()
            return
        }

        // Check frequency capping
        val currentTime = System.currentTimeMillis()
        val timeSinceLastAd = currentTime - lastAppOpenTime
        val frequencyMillis = getFrequencyMillis()

        if (lastAppOpenTime > 0 && timeSinceLastAd < frequencyMillis) {
            val remainingSeconds = (frequencyMillis - timeSinceLastAd) / 1000
            Log.w(TAG, "⏸️  App Open Ad blocked by frequency cap: ${remainingSeconds}s remaining")
            onFail()
            return
        }

        val handler = Handler(Looper.getMainLooper())
        var isTimedOut = false

        // Timeout handler
        handler.postDelayed({
            if (!isTimedOut) {
                isTimedOut = true
                Log.w(TAG, "App Open Ad timeout")
                onFail()
                preloadAppOpen()
            }
        }, timeoutMs)

        val ad = appOpenAd
        if (ad == null) {
            isTimedOut = true
            handler.removeCallbacksAndMessages(null)
            onFail()
            preloadAppOpen()
            return
        }

        isShowingAd = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                handler.removeCallbacksAndMessages(null)
                appOpenAd = null
                isShowingAd = false

                // Update timestamp for frequency capping
                lastAppOpenTime = System.currentTimeMillis()
                val frequencySeconds = getFrequencyMillis() / 1000
                Log.d(TAG, "✅ App Open Ad shown, next available in ${frequencySeconds}s")

                if (!isTimedOut) {
                    onComplete()
                }
                preloadAppOpen()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                handler.removeCallbacksAndMessages(null)
                appOpenAd = null
                isShowingAd = false
                if (!isTimedOut) {
                    onFail()
                }
                preloadAppOpen()
            }

            override fun onAdShowedFullScreenContent() {
                handler.removeCallbacksAndMessages(null)
                Log.d(TAG, "App Open Ad showed")
            }
        }

        ad.show(activity)
    }

    // ========== INTERSTITIAL AD ==========

    /**
     * Preload interstitial ad
     */
    fun preloadInterstitial() {
        if (!canRequestAds || isLoadingInterstitial || interstitialAd != null) return

        isLoadingInterstitial = true
        val request = AdRequest.Builder().build()

        InterstitialAd.load(
            application,
            RemoteConfigManager.getInterAdUnit(),
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoadingInterstitial = false
                    Log.d(TAG, "Interstitial Ad loaded")

                    // Log which network loaded the ad
                    logAdResponseInfo("INTERSTITIAL AD", ad.responseInfo)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isLoadingInterstitial = false
                    Log.e(TAG, "❌ Interstitial Ad failed: ${error.message} (Code: ${error.code})")
                    Log.e(TAG, "   └─ Domain: ${error.domain}")
                }
            }
        )
    }

    /**
     * Show interstitial ad with timeout and frequency capping
     */
    fun showInterstitial(
        activity: Activity,
        timeoutMs: Long = 800,
        onComplete: () -> Unit,
        onFail: () -> Unit
    ) {
        if (!canRequestAds || isShowingAd) {
            onFail()
            return
        }

        // Check frequency capping
        val currentTime = System.currentTimeMillis()
        val timeSinceLastAd = currentTime - lastInterstitialTime
        val frequencyMillis = getFrequencyMillis()

        if (lastInterstitialTime > 0 && timeSinceLastAd < frequencyMillis) {
            val remainingSeconds = (frequencyMillis - timeSinceLastAd) / 1000
            Log.w(TAG, "⏸️  Interstitial Ad blocked by frequency cap: ${remainingSeconds}s remaining")
            onFail()
            return
        }

        val handler = Handler(Looper.getMainLooper())
        var isTimedOut = false

        // Timeout handler
        handler.postDelayed({
            if (!isTimedOut) {
                isTimedOut = true
                Log.w(TAG, "Interstitial Ad timeout")
                onFail()
                preloadInterstitial()
            }
        }, timeoutMs)

        val ad = interstitialAd
        if (ad == null) {
            isTimedOut = true
            handler.removeCallbacksAndMessages(null)
            onFail()
            preloadInterstitial()
            return
        }

        isShowingAd = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                handler.removeCallbacksAndMessages(null)
                interstitialAd = null
                isShowingAd = false

                // Update timestamp for frequency capping
                lastInterstitialTime = System.currentTimeMillis()
                val frequencySeconds = getFrequencyMillis() / 1000
                Log.d(TAG, "✅ Interstitial Ad shown, next available in ${frequencySeconds}s")

                if (!isTimedOut) {
                    onComplete()
                }
                preloadInterstitial()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                handler.removeCallbacksAndMessages(null)
                interstitialAd = null
                isShowingAd = false
                if (!isTimedOut) {
                    onFail()
                }
                preloadInterstitial()
            }

            override fun onAdShowedFullScreenContent() {
                handler.removeCallbacksAndMessages(null)
                Log.d(TAG, "Interstitial Ad showed")
            }
        }

        ad.show(activity)
    }

    // ========== REWARDED AD ==========

    /**
     * Preload rewarded ad
     */
    fun preloadRewarded() {
        if (!canRequestAds || isLoadingRewarded || rewardedAd != null) return

        isLoadingRewarded = true
        val request = AdRequest.Builder().build()

        RewardedAd.load(
            application,
            RemoteConfigManager.getRewardAdUnit(),
            request,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isLoadingRewarded = false
                    Log.d(TAG, "Rewarded Ad loaded")

                    // Log which network loaded the ad
                    logAdResponseInfo("REWARDED AD", ad.responseInfo)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    isLoadingRewarded = false
                    Log.e(TAG, "❌ Rewarded Ad failed: ${error.message} (Code: ${error.code})")
                    Log.e(TAG, "   └─ Domain: ${error.domain}")
                }
            }
        )
    }

    /**
     * Show rewarded ad with timeout and reward callback
     */
    fun showRewarded(
        activity: Activity,
        timeoutMs: Long = 1500,
        onRewarded: (Int, String) -> Unit,
        onComplete: () -> Unit,
        onFail: () -> Unit
    ) {
        if (!canRequestAds || isShowingAd) {
            onFail()
            return
        }

        val handler = Handler(Looper.getMainLooper())
        var isTimedOut = false
        var wasRewarded = false

        // Timeout handler
        handler.postDelayed({
            if (!isTimedOut) {
                isTimedOut = true
                Log.w(TAG, "Rewarded Ad timeout")
                onFail()
                preloadRewarded()
            }
        }, timeoutMs)

        val ad = rewardedAd
        if (ad == null) {
            isTimedOut = true
            handler.removeCallbacksAndMessages(null)
            onFail()
            preloadRewarded()
            return
        }

        isShowingAd = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                handler.removeCallbacksAndMessages(null)
                rewardedAd = null
                isShowingAd = false
                if (!isTimedOut) {
                    if (wasRewarded) {
                        onComplete()
                    } else {
                        onFail()
                    }
                }
                preloadRewarded()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                handler.removeCallbacksAndMessages(null)
                rewardedAd = null
                isShowingAd = false
                if (!isTimedOut) {
                    onFail()
                }
                preloadRewarded()
            }

            override fun onAdShowedFullScreenContent() {
                handler.removeCallbacksAndMessages(null)
                Log.d(TAG, "Rewarded Ad showed")
            }
        }

        ad.show(activity) { reward ->
            wasRewarded = true
            onRewarded(reward.amount, reward.type)
            Log.d(TAG, "User earned reward: ${reward.amount} ${reward.type}")
        }
    }

    // ========== NATIVE AD ==========

    /**
     * Load native ad with custom layout binding using NativeAdBinder.
     *
     * Apps provide their own custom layout (with unique styling) via layoutId,
     * and this method handles inflating the layout and populating it with ad data.
     *
     * ## Required View IDs in App's Layout:
     * - R.id.ad_headline (TextView)
     * - R.id.ad_body (TextView)
     * - R.id.ad_call_to_action (Button)
     *
     * ## Optional View IDs:
     * - R.id.ad_app_icon (ImageView)
     * - R.id.ad_stars (RatingBar)
     * - R.id.ad_advertiser (TextView)
     * - R.id.ad_media_view (MediaView)
     *
     * @param container ViewGroup where the native ad will be displayed
     * @param layoutId Layout resource ID from app (e.g., R.layout.ad_native_blue)
     * @param hideEmptyFields If true, hides optional fields when data is missing
     * @param onLoaded Callback with loaded NativeAd and container
     * @param onFail Callback when ad fails to load
     */
    fun loadNative(
        container: ViewGroup,
        layoutId: Int,
        hideEmptyFields: Boolean = true,
        onLoaded: (NativeAd, ViewGroup) -> Unit,
        onFail: () -> Unit
    ) {
        if (!canRequestAds) {
            Log.w(TAG, "Cannot load native ad: SDK not ready")
            onFail()
            return
        }

        val adLoader = AdLoader.Builder(application, RemoteConfigManager.getNativeAdUnit())
            .forNativeAd { nativeAd ->
                Log.d(TAG, "Native Ad loaded")

                // Log which network loaded the ad
                logAdResponseInfo("NATIVE AD", nativeAd.responseInfo)

                // Inflate app's custom layout
                val adView = LayoutInflater.from(container.context)
                    .inflate(layoutId, container, false) as NativeAdView

                // Use NativeAdBinder to populate the layout
                NativeAdBinder.bind(nativeAd, adView, hideEmptyFields)

                // Add to container and show
                container.removeAllViews()
                container.addView(adView)

                Log.d(TAG, "✅ Native ad displayed in container")

                onLoaded(nativeAd, container)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "❌ Native Ad failed: ${error.message} (Code: ${error.code})")
                    Log.e(TAG, "   └─ Domain: ${error.domain}")
                    onFail()
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .build()
            )
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }
}

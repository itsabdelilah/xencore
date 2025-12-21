package io.ads.mediation.billing

import android.content.Context
import android.util.Log
import io.ads.mediation.RemoteConfigManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages premium feature unlock states
 *
 * Simple boolean flag for feature access with SharedPreferences persistence
 * and StateFlow for reactive UI updates
 */
object PremiumFeaturesManager {

    private const val TAG = "PremiumFeaturesManager"
    private const val PREFS_NAME = "premium_features_prefs"
    private const val KEY_HAS_SUBSCRIPTION = "has_active_subscription"

    private var context: Context? = null
    private val prefs by lazy {
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: throw IllegalStateException("PremiumFeaturesManager not initialized. Call init(context) first.")
    }

    // State flows for reactive UI updates
    private val _hasActiveSubscription = MutableStateFlow(false)
    val hasActiveSubscription: StateFlow<Boolean> = _hasActiveSubscription.asStateFlow()

    private var isInitialized = false

    /**
     * Initialize the manager with application context
     * Must be called before any other methods
     */
    @Synchronized
    fun init(context: Context) {
        if (isInitialized) {
            Log.d(TAG, "PremiumFeaturesManager already initialized")
            return
        }

        this.context = context.applicationContext

        // Load initial state from SharedPreferences
        _hasActiveSubscription.value = prefs.getBoolean(KEY_HAS_SUBSCRIPTION, false)
        isInitialized = true

        Log.d(TAG, "PremiumFeaturesManager initialized - hasSubscription: ${_hasActiveSubscription.value}")
    }

    /**
     * Update subscription status
     * Called by SubscriptionManager when subscription changes
     */
    fun updateSubscriptionStatus(isActive: Boolean) {
        checkInitialized()
        _hasActiveSubscription.value = isActive
        prefs.edit().putBoolean(KEY_HAS_SUBSCRIPTION, isActive).apply()

        Log.d(TAG, "Subscription status updated: $isActive")
    }

    /**
     * Check if user has premium access
     */
    fun hasPremiumAccess(): Boolean {
        checkInitialized()
        return _hasActiveSubscription.value
    }

    /**
     * Clear premium status (on subscription expiry)
     */
    fun clearPremiumStatus() {
        checkInitialized()
        updateSubscriptionStatus(false)
        Log.d(TAG, "Premium status cleared")
    }

    /**
     * Checks if premium access is required based on Remote Config setting.
     * Returns true if:
     * - premium_mode = true AND user has active subscription
     * - premium_mode = false (all users have access via ads)
     */
    fun hasPremiumAccessForFeature(): Boolean {
        val premiumModeEnabled = RemoteConfigManager.isPremiumModeEnabled()

        return if (premiumModeEnabled) {
            // Traditional IAP: require active subscription
            hasPremiumAccess()
        } else {
            // Ad-based: all users can access by watching ads
            true
        }
    }

    /**
     * Determines if user should see paywall or watch ad to access feature.
     * Returns true if premium_mode is enabled AND user has no subscription.
     */
    fun shouldShowPaywall(): Boolean {
        val premiumModeEnabled = RemoteConfigManager.isPremiumModeEnabled()
        return premiumModeEnabled && !hasPremiumAccess()
    }

    /**
     * Determines if user should watch an ad to unlock feature.
     * Returns true if premium_mode is disabled AND user has no subscription.
     */
    fun shouldShowAdForFeature(): Boolean {
        val premiumModeEnabled = RemoteConfigManager.isPremiumModeEnabled()
        return !premiumModeEnabled && !hasPremiumAccess()
    }

    private fun checkInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("PremiumFeaturesManager not initialized. Call init(context) first.")
        }
    }
}

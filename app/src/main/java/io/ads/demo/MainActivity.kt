package io.ads.demo

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.nativead.NativeAd
import io.ads.demo.databinding.ActivityMainBinding
import io.ads.demo.paywall.PaywallDialogFragment
import io.ads.mediation.AdsManager
import io.ads.mediation.RemoteConfigManager
import io.ads.mediation.billing.PremiumFeaturesManager
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentNativeAd: NativeAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // SDK is already initialized in SplashActivity, just preload ads
        // This ensures smooth UX - SDK init happens during splash loading
        AdsManager.preloadAppOpen()
        AdsManager.preloadInterstitial()
        AdsManager.preloadRewarded()

        // Show frequency capping info
        val frequencySeconds = RemoteConfigManager.getAdFrequencySeconds()
        updateStatus("Ready - Ad frequency: ${frequencySeconds}s between ads")

        setupClickListeners()
        observePremiumStatus()
    }

    private fun setupClickListeners() {
        // Upgrade button - shows paywall
        binding.upgradeButton.setOnClickListener {
            showPaywall()
        }

        // Premium feature: Export Data
        binding.featureExportCard.setOnClickListener {
            handlePremiumFeature(
                featureName = "Export Data",
                onUnlocked = {
                    showToast("Exporting data to CSV...")
                    updateStatus("Export feature activated")
                }
            )
        }

        // Premium feature: Cloud Sync
        binding.featureCloudCard.setOnClickListener {
            handlePremiumFeature(
                featureName = "Cloud Sync",
                onUnlocked = {
                    showToast("Syncing to cloud...")
                    updateStatus("Cloud sync started")
                }
            )
        }

        // Premium feature: Dark Mode
        binding.featureDarkModeCard.setOnClickListener {
            handlePremiumFeature(
                featureName = "Dark Mode",
                onUnlocked = {
                    binding.featureDarkModeSwitch.isChecked = !binding.featureDarkModeSwitch.isChecked
                    val mode = if (binding.featureDarkModeSwitch.isChecked) "Dark" else "Light"
                    showToast("$mode mode activated")
                    updateStatus("Dark mode toggled")
                }
            )
        }

        binding.featureDarkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (PremiumFeaturesManager.hasPremiumAccess()) {
                val mode = if (isChecked) "enabled" else "disabled"
                showToast("Dark mode $mode")
                updateStatus("Dark mode $mode")
            }
        }

        // App Open Ad
        binding.btnAppOpen.setOnClickListener {
            updateStatus("Loading App Open Ad...")
            AdsManager.showAppOpen(
                activity = this,
                onComplete = {
                    updateStatus("App Open Ad completed")
                    showToast("App Open Ad completed")
                },
                onFail = {
                    // Check if blocked by frequency cap
                    val remainingSeconds = AdsManager.getAppOpenRemainingSeconds()
                    if (remainingSeconds > 0) {
                        updateStatus("App Open blocked: Please wait ${remainingSeconds}s")
                        showToast("Please wait ${remainingSeconds}s")
                    } else {
                        updateStatus("App Open Ad failed or timeout")
                        showToast("App Open Ad failed")
                    }
                }
            )
        }

        // Interstitial Ad
        binding.btnInterstitial.setOnClickListener {
            updateStatus("Loading Interstitial Ad...")
            AdsManager.showInterstitial(
                activity = this,
                onComplete = {
                    updateStatus("Interstitial Ad completed")
                    showToast("Interstitial Ad completed")
                },
                onFail = {
                    // Check if blocked by frequency cap
                    val remainingSeconds = AdsManager.getInterstitialRemainingSeconds()
                    if (remainingSeconds > 0) {
                        updateStatus("Interstitial blocked: Please wait ${remainingSeconds}s")
                        showToast("Please wait ${remainingSeconds}s")
                    } else {
                        updateStatus("Interstitial Ad failed or timeout")
                        showToast("Interstitial Ad failed")
                    }
                }
            )
        }

        // Rewarded Ad
        binding.btnRewarded.setOnClickListener {
            updateStatus("Loading Rewarded Ad...")
            AdsManager.showRewarded(
                activity = this,
                onRewarded = { amount, type ->
                    updateStatus("User earned reward: $amount $type")
                    showToast("Earned $amount $type")
                },
                onComplete = {
                    updateStatus("Rewarded Ad completed")
                    showToast("Rewarded Ad completed")
                },
                onFail = {
                    updateStatus("Rewarded Ad failed, timeout, or dismissed")
                    showToast("Rewarded Ad failed")
                }
            )
        }

        // Native Ad
        binding.btnNative.setOnClickListener {
            updateStatus("Loading Native Ad...")
            binding.btnNative.isEnabled = false

            // Show shimmer loading effect
            showNativeAdShimmer()

            AdsManager.loadNative(
                container = binding.nativeAdContainer,
                layoutId = R.layout.ad_native_custom,
                onLoaded = { nativeAd, container ->
                    updateStatus("Native Ad loaded")
                    showToast("Native Ad loaded")

                    // Store reference and hide shimmer
                    // Note: Library handles inflating and binding automatically
                    currentNativeAd?.destroy()
                    currentNativeAd = nativeAd
                    hideNativeAdShimmer()
                    container.visibility = View.VISIBLE

                    binding.btnNative.isEnabled = true
                },
                onFail = {
                    updateStatus("Native Ad failed to load")
                    showToast("Native Ad failed")

                    // Hide shimmer and container on failure
                    hideNativeAdShimmer()
                    binding.nativeAdContainer.visibility = View.GONE

                    binding.btnNative.isEnabled = true
                }
            )
        }
    }

    /**
     * Observe premium status changes and update UI accordingly
     */
    private fun observePremiumStatus() {
        PremiumFeaturesManager.hasActiveSubscription
            .onEach { isPremium ->
                updatePremiumUI(isPremium)
            }
            .launchIn(lifecycleScope)
    }

    /**
     * Update UI based on premium status
     */
    private fun updatePremiumUI(isPremium: Boolean) {
        if (isPremium) {
            // Premium active - status banner
            binding.statusBanner.setCardBackgroundColor(Color.parseColor("#E8F5E9"))
            binding.statusIcon.text = "✓"
            binding.statusIcon.setTextColor(Color.parseColor("#4CAF50"))
            binding.statusTitle.text = "Premium Active"
            binding.statusTitle.setTextColor(Color.parseColor("#4CAF50"))
            binding.statusSubtitle.text = "Ads are disabled"
            binding.upgradeButton.text = "Manage"

            // Unlock premium features
            binding.featureExportLock.text = "✓"
            binding.featureExportLock.setTextColor(Color.parseColor("#4CAF50"))
            binding.featureCloudLock.text = "✓"
            binding.featureCloudLock.setTextColor(Color.parseColor("#4CAF50"))
            binding.featureDarkModeSwitch.isEnabled = true
        } else {
            // Check if ad-unlock mode is enabled
            val premiumModeEnabled = RemoteConfigManager.isPremiumModeEnabled()

            if (premiumModeEnabled) {
                // Free version - IAP mode (paywall)
                binding.statusBanner.setCardBackgroundColor(Color.parseColor("#E3F2FD"))
                binding.statusIcon.text = "★"
                binding.statusIcon.setTextColor(Color.parseColor("#1976D2"))
                binding.statusTitle.text = "Free Version"
                binding.statusTitle.setTextColor(Color.parseColor("#1976D2"))
                binding.statusSubtitle.text = "Upgrade to remove ads"
                binding.upgradeButton.text = "Upgrade"
            } else {
                // Ad-unlock mode
                binding.statusBanner.setCardBackgroundColor(Color.parseColor("#FFF3E0"))
                binding.statusIcon.text = "📺"
                binding.statusIcon.setTextColor(Color.parseColor("#FF9800"))
                binding.statusTitle.text = "Ad-Unlock Mode"
                binding.statusTitle.setTextColor(Color.parseColor("#FF9800"))
                binding.statusSubtitle.text = "Watch ads to unlock features"
                binding.upgradeButton.text = "Upgrade"
            }

            // Lock premium features (show lock icon in both modes)
            binding.featureExportLock.text = "🔒"
            binding.featureExportLock.setTextColor(Color.parseColor("#666666"))
            binding.featureCloudLock.text = "🔒"
            binding.featureCloudLock.setTextColor(Color.parseColor("#666666"))
            binding.featureDarkModeSwitch.isEnabled = false
            binding.featureDarkModeSwitch.isChecked = false
        }
    }

    /**
     * Show the paywall dialog
     */
    private fun showPaywall() {
        val paywall = PaywallDialogFragment.newInstance()
        paywall.onSubscriptionSuccess = {
            showToast("Premium features unlocked!")
            updateStatus("Premium active - Ads disabled")
        }
        paywall.show(supportFragmentManager, "paywall")
    }

    /**
     * Unified handler for premium features with Remote Config-based behavior.
     *
     * Behavior based on Remote Config "premium_mode" parameter:
     * - premium_mode = true: Show paywall if no subscription
     * - premium_mode = false: Show interstitial ad to unlock feature
     *
     * @param featureName Name of the feature being accessed (for logging)
     * @param onUnlocked Lambda to execute when feature is unlocked
     */
    private fun handlePremiumFeature(featureName: String, onUnlocked: () -> Unit) {
        // Check if user already has premium access (subscription)
        if (PremiumFeaturesManager.hasPremiumAccess()) {
            // Premium user - direct access
            onUnlocked()
            return
        }

        // Check Remote Config to determine unlock method
        if (PremiumFeaturesManager.shouldShowPaywall()) {
            // Premium mode enabled - show paywall
            showPaywall()
        } else {
            // Ad-based mode - show interstitial ad to unlock
            showInterstitialForFeatureUnlock(featureName, onUnlocked)
        }
    }

    /**
     * Shows an interstitial ad before unlocking a premium feature.
     * If ad fails to load or display, still grants access to the feature.
     *
     * @param featureName Name of the feature (for logging)
     * @param onUnlocked Lambda to execute after ad is shown or on failure
     */
    private fun showInterstitialForFeatureUnlock(featureName: String, onUnlocked: () -> Unit) {
        showToast("Loading ad to unlock $featureName...")
        updateStatus("Preparing ad for $featureName...")

        AdsManager.showInterstitial(
            activity = this,
            onComplete = {
                // Ad shown successfully
                showToast("Ad completed! $featureName unlocked")
                onUnlocked()
            },
            onFail = {
                // Ad failed to load/show - still grant access
                showToast("Ad not available, unlocking $featureName anyway")
                onUnlocked()
            }
        )
    }

    private fun updateStatus(message: String) {
        binding.tvStatus.text = "Status: $message"
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Show shimmer loading effect for native ad
     */
    private fun showNativeAdShimmer() {
        binding.nativeAdContainer.visibility = View.VISIBLE
        binding.shimmerPlaceholder.root.visibility = View.VISIBLE
        binding.shimmerPlaceholder.shimmerViewContainer.startShimmer()
    }

    /**
     * Hide shimmer loading effect
     */
    private fun hideNativeAdShimmer() {
        binding.shimmerPlaceholder.shimmerViewContainer.stopShimmer()
        binding.shimmerPlaceholder.root.visibility = View.GONE
    }

    override fun onDestroy() {
        // Clean up native ad
        currentNativeAd?.destroy()

        // Stop shimmer animation
        binding.shimmerPlaceholder.shimmerViewContainer.stopShimmer()

        super.onDestroy()
    }
}

package io.ads.demo

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.gms.ads.nativead.NativeAd
import io.ads.mediation.AdsManager
import io.ads.mediation.RemoteConfigManager

class MainActivity : AppCompatActivity() {

    private lateinit var btnAppOpen: Button
    private lateinit var btnInterstitial: Button
    private lateinit var btnRewarded: Button
    private lateinit var btnNative: Button
    private lateinit var tvStatus: TextView
    private lateinit var nativeAdContainer: FrameLayout
    private lateinit var shimmerPlaceholder: ShimmerFrameLayout

    private var currentNativeAd: NativeAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Initialize views
        btnAppOpen = findViewById(R.id.btnAppOpen)
        btnInterstitial = findViewById(R.id.btnInterstitial)
        btnRewarded = findViewById(R.id.btnRewarded)
        btnNative = findViewById(R.id.btnNative)
        tvStatus = findViewById(R.id.tvStatus)
        nativeAdContainer = findViewById(R.id.nativeAdContainer)
        shimmerPlaceholder = findViewById(R.id.shimmerPlaceholder)

        // SDK is already initialized in SplashActivity, just preload ads
        // This ensures smooth UX - SDK init happens during splash loading
        AdsManager.preloadAppOpen()
        AdsManager.preloadInterstitial()
        AdsManager.preloadRewarded()

        // Show frequency capping info
        val frequencySeconds = RemoteConfigManager.getAdFrequencySeconds()
        updateStatus("Ready - Ad frequency: ${frequencySeconds}s between ads")

        setupClickListeners()
    }

    private fun setupClickListeners() {
        // App Open Ad
        btnAppOpen.setOnClickListener {
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
        btnInterstitial.setOnClickListener {
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
        btnRewarded.setOnClickListener {
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
        btnNative.setOnClickListener {
            updateStatus("Loading Native Ad...")
            btnNative.isEnabled = false

            // Show shimmer loading effect
            showNativeAdShimmer()

            AdsManager.loadNative(
                container = nativeAdContainer,
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

                    btnNative.isEnabled = true
                },
                onFail = {
                    updateStatus("Native Ad failed to load")
                    showToast("Native Ad failed")

                    // Hide shimmer and container on failure
                    hideNativeAdShimmer()
                    nativeAdContainer.visibility = View.GONE

                    btnNative.isEnabled = true
                }
            )
        }
    }

    private fun updateStatus(message: String) {
        tvStatus.text = "Status: $message"
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Show shimmer loading effect for native ad
     */
    private fun showNativeAdShimmer() {
        nativeAdContainer.visibility = View.VISIBLE
        shimmerPlaceholder.visibility = View.VISIBLE
        shimmerPlaceholder.startShimmer()
    }

    /**
     * Hide shimmer loading effect
     */
    private fun hideNativeAdShimmer() {
        shimmerPlaceholder.stopShimmer()
        shimmerPlaceholder.visibility = View.GONE
    }

    override fun onDestroy() {
        // Clean up native ad
        currentNativeAd?.destroy()

        // Stop shimmer animation
        if (::shimmerPlaceholder.isInitialized) {
            shimmerPlaceholder.stopShimmer()
        }

        super.onDestroy()
    }
}
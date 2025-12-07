package io.ads.demo

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import io.ads.mediation.AdsManager

/**
 * Splash Activity that handles async initialization of the ads SDK.
 *
 * This activity:
 * 1. Shows immediate loading feedback to the user
 * 2. Fetches Remote Config asynchronously (with timeout)
 * 3. Attempts AdMob App ID override if configured
 * 4. Initializes UMP consent
 * 5. Initializes MobileAds SDK
 * 6. Navigates to MainActivity when ready
 *
 * Benefits over blocking in Application.onCreate():
 * - User sees immediate visual feedback (not frozen app)
 * - Professional loading experience
 * - Graceful timeout handling
 * - Better perceived performance
 */
class SplashActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SplashActivity"
        private const val INITIALIZATION_TIMEOUT_MS = 5000L // 5 seconds max
        private const val MIN_SPLASH_DURATION_MS = 1500L // Minimum 1.5s for branding
    }

    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private var isInitializationComplete = false
    private val handler = Handler(Looper.getMainLooper())
    private val startTime = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)

        // Initialize views
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)

        Log.d(TAG, "════════════════════════════════════")
        Log.d(TAG, "🚀 SplashActivity Starting")
        Log.d(TAG, "════════════════════════════════════")

        // Start initialization process
        startInitialization()

        // Set timeout to ensure we don't hang forever
        handler.postDelayed({
            if (!isInitializationComplete) {
                Log.w(TAG, "⏱️ Initialization timeout - proceeding to MainActivity")
                proceedToMainActivity()
            }
        }, INITIALIZATION_TIMEOUT_MS)
    }

    /**
     * Start the async initialization process
     */
    private fun startInitialization() {
        updateStatus("Initializing...")

        // Initialize AdsManager on splash (with Remote Config fetch + App ID override)
        AdsManager.initializeOnSplash(
            activity = this,
            onProgress = { message ->
                // Update UI with progress messages
                runOnUiThread {
                    updateStatus(message)
                }
            },
            onComplete = { success ->
                Log.d(TAG, if (success) {
                    "✅ Initialization complete successfully"
                } else {
                    "⚠️ Initialization complete with warnings"
                })

                // Ensure minimum splash duration for branding
                val elapsed = System.currentTimeMillis() - startTime
                val remainingTime = MIN_SPLASH_DURATION_MS - elapsed

                if (remainingTime > 0) {
                    handler.postDelayed({
                        proceedToMainActivity()
                    }, remainingTime)
                } else {
                    proceedToMainActivity()
                }
            }
        )
    }

    /**
     * Update the status text shown to user
     */
    private fun updateStatus(message: String) {
        tvStatus.text = message
        Log.d(TAG, "Status: $message")
    }

    /**
     * Navigate to MainActivity and finish splash
     */
    private fun proceedToMainActivity() {
        if (isInitializationComplete) {
            return // Already navigated
        }

        isInitializationComplete = true
        handler.removeCallbacksAndMessages(null) // Clear any pending callbacks

        Log.d(TAG, "════════════════════════════════════")
        Log.d(TAG, "➡️ Proceeding to MainActivity")
        Log.d(TAG, "════════════════════════════════════")

        // Launch MainActivity
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)

        // Finish splash
        finish()

        // Optional: Add transition animation
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onDestroy() {
        // Clean up handler callbacks
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
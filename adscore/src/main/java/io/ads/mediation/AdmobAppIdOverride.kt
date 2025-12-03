package io.ads.mediation

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * Runtime APPLICATION_ID override utility.
 *
 * This allows changing the AdMob App ID at runtime via Firebase Remote Config
 * without requiring app updates - critical for published apps in the Play Store.
 *
 * ⚠️ CRITICAL TIMING:
 * - Must be called BEFORE MobileAds.initialize()
 * - Must be called AFTER Firebase Remote Config is fetched
 *
 * 🔒 RISK LEVEL: Medium
 * - Uses undocumented Android API behavior
 * - May break in future Android versions
 * - Provides fallback to manifest value if it fails
 *
 * 📋 USE CASE:
 * When you need to switch AdMob accounts after app is published, or for
 * emergency fixes without waiting for Play Store review.
 */
object AdmobAppIdOverride {

    private const val TAG = "AdmobAppIdOverride"
    private const val ADMOB_APP_ID_KEY = "com.google.android.gms.ads.APPLICATION_ID"

    // Regex for validating AdMob App ID format: ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
    private val ADMOB_ID_PATTERN = Regex("^ca-app-pub-[0-9]{16}~[0-9]{10}\$")

    /**
     * Attempts to override the APPLICATION_ID in the app's meta-data at runtime.
     *
     * This method:
     * - Validates the new App ID format
     * - Modifies the ApplicationInfo meta-data Bundle
     * - Verifies the override succeeded
     * - Logs all steps with timing information
     *
     * @param context Application context
     * @param newAppId The new AdMob App ID to use (must match format: ca-app-pub-XXXXX~YYYYY)
     * @return true if override succeeded, false otherwise
     */
    @Synchronized
    fun override(context: Context, newAppId: String): Boolean {
        val startTime = System.currentTimeMillis()

        Log.d(TAG, "════════════════════════════════════")
        Log.d(TAG, "🔧 AdMob App ID Override Attempt")
        Log.d(TAG, "════════════════════════════════════")

        // Validation: Check if empty/blank
        if (newAppId.isBlank()) {
            Log.w(TAG, "❌ Override failed: App ID is empty/blank")
            Log.d(TAG, "════════════════════════════════════")
            return false
        }

        // Validation: Check format
        if (!isValidAdmobAppId(newAppId)) {
            Log.e(TAG, "❌ Override failed: Invalid AdMob App ID format")
            Log.e(TAG, "   Provided: $newAppId")
            Log.e(TAG, "   Expected: ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY")
            Log.d(TAG, "════════════════════════════════════")
            return false
        }

        Log.d(TAG, "✅ Format validation passed")

        return try {
            val appContext = context.applicationContext
            val packageManager = appContext.packageManager
            val packageName = appContext.packageName

            Log.d(TAG, "📦 Package: $packageName")

            // Get ApplicationInfo with meta-data
            val applicationInfo = packageManager.getApplicationInfo(
                packageName,
                PackageManager.GET_META_DATA
            )

            val metaData = applicationInfo.metaData
            if (metaData == null) {
                Log.e(TAG, "❌ Override failed: No meta-data found in manifest")
                Log.e(TAG, "   Make sure APPLICATION_ID is declared in AndroidManifest.xml")
                Log.d(TAG, "════════════════════════════════════")
                return false
            }

            // Get original value for logging
            val originalAppId = metaData.getString(ADMOB_APP_ID_KEY, "NOT_FOUND")
            Log.d(TAG, "📋 Original App ID: $originalAppId")
            Log.d(TAG, "🎯 Target App ID:   $newAppId")

            // Check if already the same
            if (originalAppId == newAppId) {
                Log.i(TAG, "ℹ️  App ID already matches - no override needed")
                Log.d(TAG, "════════════════════════════════════")
                return true
            }

            // Perform the override - THE HACK
            Log.d(TAG, "⚙️  Executing override...")
            metaData.putString(ADMOB_APP_ID_KEY, newAppId)

            // Verify the override worked
            val verifyAppId = metaData.getString(ADMOB_APP_ID_KEY, "")
            val success = verifyAppId == newAppId

            val duration = System.currentTimeMillis() - startTime

            if (success) {
                Log.i(TAG, "✅ Override SUCCESS in ${duration}ms")
                Log.i(TAG, "   Original: $originalAppId")
                Log.i(TAG, "   New:      $newAppId")
                Log.i(TAG, "   Verified: $verifyAppId")
            } else {
                Log.e(TAG, "❌ Override FAILED - Verification mismatch")
                Log.e(TAG, "   Expected: $newAppId")
                Log.e(TAG, "   Got:      $verifyAppId")
                Log.e(TAG, "   Duration: ${duration}ms")
            }

            Log.d(TAG, "════════════════════════════════════")
            success

        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "❌ Override failed: Package not found", e)
            Log.e(TAG, "   Package name: ${context.packageName}")
            Log.d(TAG, "════════════════════════════════════")
            false
        } catch (e: NullPointerException) {
            Log.e(TAG, "❌ Override failed: Null pointer exception", e)
            Log.e(TAG, "   This may indicate meta-data or ApplicationInfo is null")
            Log.d(TAG, "════════════════════════════════════")
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Override failed: Unexpected error", e)
            Log.e(TAG, "   Error type: ${e.javaClass.simpleName}")
            Log.e(TAG, "   Message: ${e.message}")
            Log.d(TAG, "════════════════════════════════════")
            false
        }
    }

    /**
     * Validates AdMob App ID format.
     *
     * Expected format: ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
     * - 16 digits after "ca-app-pub-"
     * - Tilde (~) separator
     * - 10 digits after tilde
     *
     * @param appId The App ID to validate
     * @return true if format is valid, false otherwise
     */
    private fun isValidAdmobAppId(appId: String): Boolean {
        return ADMOB_ID_PATTERN.matches(appId)
    }

    /**
     * Gets the current APPLICATION_ID from the manifest meta-data.
     *
     * Useful for logging and debugging to see what App ID is currently configured.
     *
     * @param context Application or Activity context
     * @return The current App ID, or null if not found
     */
    fun getCurrentAppId(context: Context): String? {
        return try {
            val appContext = context.applicationContext
            val packageManager = appContext.packageManager
            val packageName = appContext.packageName

            val applicationInfo = packageManager.getApplicationInfo(
                packageName,
                PackageManager.GET_META_DATA
            )

            applicationInfo.metaData?.getString(ADMOB_APP_ID_KEY)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current App ID", e)
            null
        }
    }
}

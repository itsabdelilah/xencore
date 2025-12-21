package io.ads.mediation.billing

import android.util.Log
import com.android.billingclient.api.BillingClient

/**
 * Billing Error Handler
 *
 * Converts technical BillingResponseCode errors into user-friendly messages
 * Provides actionable suggestions for users
 */
object BillingErrorHandler {

    private const val TAG = "BillingErrorHandler"

    /**
     * Error types for different handling strategies
     */
    enum class ErrorType {
        NETWORK,           // Network connectivity issues
        USER_ACTION,       // User cancelled or similar
        PRODUCT_CONFIG,    // Product configuration issues
        BILLING_UNAVAILABLE, // Google Play Billing not available
        PURCHASE_ISSUE,    // Purchase-specific problems
        UNKNOWN           // Unknown/unhandled errors
    }

    /**
     * Error information with user-friendly message and type
     */
    data class BillingError(
        val code: Int,
        val technicalMessage: String,
        val userMessage: String,
        val errorType: ErrorType,
        val canRetry: Boolean,
        val suggestion: String? = null
    )

    /**
     * Convert BillingResponseCode to user-friendly error
     */
    fun getError(responseCode: Int, debugMessage: String = ""): BillingError {
        return when (responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                BillingError(
                    code = responseCode,
                    technicalMessage = "OK",
                    userMessage = "Success",
                    errorType = ErrorType.USER_ACTION,
                    canRetry = false
                )
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                BillingError(
                    code = responseCode,
                    technicalMessage = "User cancelled",
                    userMessage = "Purchase cancelled",
                    errorType = ErrorType.USER_ACTION,
                    canRetry = true,
                    suggestion = "Tap Continue to try again"
                )
            }

            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> {
                BillingError(
                    code = responseCode,
                    technicalMessage = debugMessage,
                    userMessage = "Google Play services unavailable",
                    errorType = ErrorType.NETWORK,
                    canRetry = true,
                    suggestion = "Check your internet connection and try again"
                )
            }

            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                BillingError(
                    code = responseCode,
                    technicalMessage = debugMessage,
                    userMessage = "Billing not available",
                    errorType = ErrorType.BILLING_UNAVAILABLE,
                    canRetry = true,
                    suggestion = "Update Google Play Store and try again"
                )
            }

            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> {
                BillingError(
                    code = responseCode,
                    technicalMessage = debugMessage,
                    userMessage = "Subscription temporarily unavailable",
                    errorType = ErrorType.PRODUCT_CONFIG,
                    canRetry = true,
                    suggestion = "This subscription is being set up. Please try again later"
                )
            }

            BillingClient.BillingResponseCode.DEVELOPER_ERROR -> {
                BillingError(
                    code = responseCode,
                    technicalMessage = debugMessage,
                    userMessage = "Configuration error",
                    errorType = ErrorType.PRODUCT_CONFIG,
                    canRetry = false,
                    suggestion = "Please contact support"
                )
            }

            BillingClient.BillingResponseCode.ERROR -> {
                BillingError(
                    code = responseCode,
                    technicalMessage = debugMessage,
                    userMessage = "An error occurred",
                    errorType = ErrorType.UNKNOWN,
                    canRetry = true,
                    suggestion = "Please try again"
                )
            }

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                BillingError(
                    code = responseCode,
                    technicalMessage = "Item already owned",
                    userMessage = "You already have an active subscription",
                    errorType = ErrorType.PURCHASE_ISSUE,
                    canRetry = false,
                    suggestion = "Your subscription is already active"
                )
            }

            BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> {
                BillingError(
                    code = responseCode,
                    technicalMessage = "Item not owned",
                    userMessage = "No active subscription found",
                    errorType = ErrorType.PURCHASE_ISSUE,
                    canRetry = false,
                    suggestion = "Subscribe to start using premium features"
                )
            }

            BillingClient.BillingResponseCode.NETWORK_ERROR -> {
                BillingError(
                    code = responseCode,
                    technicalMessage = debugMessage,
                    userMessage = "Network connection error",
                    errorType = ErrorType.NETWORK,
                    canRetry = true,
                    suggestion = "Check your internet connection and try again"
                )
            }

            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> {
                BillingError(
                    code = responseCode,
                    technicalMessage = "Service disconnected",
                    userMessage = "Connection lost",
                    errorType = ErrorType.NETWORK,
                    canRetry = true,
                    suggestion = "Reconnecting... Please wait"
                )
            }

            BillingClient.BillingResponseCode.SERVICE_TIMEOUT -> {
                BillingError(
                    code = responseCode,
                    technicalMessage = "Service timeout",
                    userMessage = "Request timed out",
                    errorType = ErrorType.NETWORK,
                    canRetry = true,
                    suggestion = "Please try again"
                )
            }

            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> {
                BillingError(
                    code = responseCode,
                    technicalMessage = debugMessage,
                    userMessage = "Feature not supported",
                    errorType = ErrorType.BILLING_UNAVAILABLE,
                    canRetry = false,
                    suggestion = "Update Google Play Store or use a different device"
                )
            }

            else -> {
                // Unknown error code
                BillingError(
                    code = responseCode,
                    technicalMessage = debugMessage,
                    userMessage = "Something went wrong",
                    errorType = ErrorType.UNKNOWN,
                    canRetry = true,
                    suggestion = "Please try again later"
                )
            }
        }
    }

    /**
     * Get user-friendly error message
     */
    fun getUserMessage(responseCode: Int, debugMessage: String = ""): String {
        val error = getError(responseCode, debugMessage)
        return if (error.suggestion != null) {
            "${error.userMessage}. ${error.suggestion}"
        } else {
            error.userMessage
        }
    }

    /**
     * Log error with appropriate level based on severity
     */
    fun logError(responseCode: Int, debugMessage: String = "", context: String = "") {
        val error = getError(responseCode, debugMessage)

        val logMessage = buildString {
            append("════════════════════════════════════════\n")
            append("Billing Error: $context\n")
            append("════════════════════════════════════════\n")
            append("Error Type: ${error.errorType}\n")
            append("Response Code: ${error.code}\n")
            append("User Message: ${error.userMessage}\n")
            if (error.suggestion != null) {
                append("Suggestion: ${error.suggestion}\n")
            }
            if (debugMessage.isNotEmpty()) {
                append("Technical: ${error.technicalMessage}\n")
            }
            append("Can Retry: ${error.canRetry}\n")
            append("════════════════════════════════════════")
        }

        when (error.errorType) {
            ErrorType.NETWORK, ErrorType.USER_ACTION -> {
                Log.w(TAG, logMessage)
            }
            ErrorType.PRODUCT_CONFIG, ErrorType.BILLING_UNAVAILABLE -> {
                Log.e(TAG, logMessage)
            }
            else -> {
                Log.e(TAG, logMessage)
            }
        }
    }

    /**
     * Check if error is recoverable and should trigger retry
     */
    fun shouldAutoRetry(responseCode: Int): Boolean {
        val error = getError(responseCode)
        return error.canRetry && (
            error.errorType == ErrorType.NETWORK ||
            error.errorType == ErrorType.BILLING_UNAVAILABLE
        )
    }

    /**
     * Get retry delay based on error type (in milliseconds)
     */
    fun getRetryDelay(responseCode: Int, attemptNumber: Int): Long {
        val error = getError(responseCode)
        return when (error.errorType) {
            ErrorType.NETWORK -> {
                // Exponential backoff: 2s, 4s, 8s, 16s
                minOf(2000L * (1 shl attemptNumber), 16000L)
            }
            ErrorType.BILLING_UNAVAILABLE -> {
                // Slower retry: 5s, 10s, 20s
                minOf(5000L * (1 shl attemptNumber), 20000L)
            }
            else -> 3000L // Default 3 second delay
        }
    }
}

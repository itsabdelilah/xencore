package io.ads.mediation.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Subscription Manager
 *
 * Core billing logic for Google Play subscription with free trial
 *
 * Key responsibilities:
 * - Connect to Google Play Billing
 * - Query product details (single product)
 * - Handle purchase flow with free trial
 * - Acknowledge purchases
 * - Update premium feature status via PremiumFeaturesManager
 */
object SubscriptionManager : PurchasesUpdatedListener {

    private const val TAG = "SubscriptionManager"
    private const val MAX_RETRY_ATTEMPTS = 3

    private var context: Context? = null
    private var productId: String = ""
    private var billingClient: BillingClient? = null
    private var coroutineScope: CoroutineScope? = null

    // Product details cache
    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    // Subscription state
    private val _subscriptionState = MutableStateFlow<SubscriptionState>(SubscriptionState.Loading)
    val subscriptionState: StateFlow<SubscriptionState> = _subscriptionState.asStateFlow()

    // Purchase state
    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Idle)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()

    private var retryAttempt = 0
    private var isInitialized = false

    /**
     * Initialize the manager with application context and product ID
     * Must be called before any other methods
     *
     * @param context Application context
     * @param productId The subscription product ID from Google Play Console
     */
    @Synchronized
    fun init(context: Context, productId: String) {
        if (isInitialized) {
            Log.d(TAG, "SubscriptionManager already initialized")
            return
        }

        this.context = context.applicationContext
        this.productId = productId
        this.coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        // Create BillingClient
        billingClient = BillingClient.newBuilder(context.applicationContext)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .enablePrepaidPlans()
                    .build()
            )
            .build()

        isInitialized = true
        Log.d(TAG, "SubscriptionManager initialized with productId: $productId")

        initializeBillingClient()
    }

    /**
     * Initialize billing client with auto-retry
     */
    private fun initializeBillingClient() {
        Log.d(TAG, "Initializing billing client (attempt ${retryAttempt + 1})")
        connectToBillingService()
    }

    /**
     * Connect to Google Play Billing service
     */
    private fun connectToBillingService() {
        val client = billingClient ?: return

        if (client.isReady) {
            Log.d(TAG, "Billing client already connected")
            onBillingServiceConnected()
            return
        }

        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing service connected successfully")
                    retryAttempt = 0
                    onBillingServiceConnected()
                } else {
                    BillingErrorHandler.logError(
                        billingResult.responseCode,
                        billingResult.debugMessage,
                        "Billing Setup"
                    )
                    handleConnectionFailure(billingResult.responseCode)
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
                handleConnectionFailure(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
            }
        })
    }

    /**
     * Handle connection failure with retry logic
     */
    private fun handleConnectionFailure(responseCode: Int) {
        if (retryAttempt < MAX_RETRY_ATTEMPTS && BillingErrorHandler.shouldAutoRetry(responseCode)) {
            val delay = BillingErrorHandler.getRetryDelay(responseCode, retryAttempt)
            Log.d(TAG, "Retrying connection in ${delay}ms...")

            coroutineScope?.launch {
                delay(delay)
                retryAttempt++
                connectToBillingService()
            }
        } else {
            Log.e(TAG, "Max retries reached or error not retryable")
            _subscriptionState.value = SubscriptionState.Error(
                BillingErrorHandler.getUserMessage(responseCode)
            )
        }
    }

    /**
     * Called when billing service successfully connects
     */
    private fun onBillingServiceConnected() {
        coroutineScope?.launch {
            queryProductDetails()
            queryPurchases()
        }
    }

    /**
     * Query subscription product details from Google Play
     */
    private suspend fun queryProductDetails() = withContext(Dispatchers.IO) {
        val client = billingClient ?: return@withContext

        try {
            val productList = listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )

            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build()

            val result = client.queryProductDetails(params)

            if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                result.productDetailsList?.firstOrNull()?.let { details ->
                    _productDetails.value = details
                    Log.d(TAG, "Product details loaded: ${details.productId}")
                    logOfferDetails(details)
                }
            } else {
                BillingErrorHandler.logError(
                    result.billingResult.responseCode,
                    result.billingResult.debugMessage,
                    "Query Product Details"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying product details", e)
        }
    }

    /**
     * Log offer details for debugging
     */
    private fun logOfferDetails(details: ProductDetails) {
        details.subscriptionOfferDetails?.forEach { offer ->
            Log.d(TAG, "Offer: ${offer.offerId ?: "base"}")
            offer.pricingPhases.pricingPhaseList.forEach { phase ->
                Log.d(TAG, "  Phase: ${phase.formattedPrice}, " +
                        "Period: ${phase.billingPeriod}, " +
                        "Cycles: ${phase.billingCycleCount}")
            }
        }
    }

    /**
     * Query existing purchases from Google Play
     */
    suspend fun queryPurchases() = withContext(Dispatchers.IO) {
        val client = billingClient ?: return@withContext

        try {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()

            val result = client.queryPurchasesAsync(params)

            if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val purchases = result.purchasesList

                if (purchases.isEmpty()) {
                    Log.d(TAG, "No existing purchases found")
                    handleNoPurchases()
                } else {
                    Log.d(TAG, "Found ${purchases.size} purchase(s)")
                    purchases.forEach { purchase ->
                        handlePurchase(purchase, isRestore = false)
                    }
                }
            } else {
                BillingErrorHandler.logError(
                    result.billingResult.responseCode,
                    result.billingResult.debugMessage,
                    "Query Purchases"
                )
                handleNoPurchases()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying purchases", e)
            handleNoPurchases()
        }
    }

    /**
     * Handle case when no purchases found
     */
    private fun handleNoPurchases() {
        PremiumFeaturesManager.clearPremiumStatus()
        _subscriptionState.value = SubscriptionState.None
    }

    /**
     * Handle purchase (new or existing)
     */
    private suspend fun handlePurchase(purchase: Purchase, isRestore: Boolean) {
        Log.d(TAG, "Processing purchase - State: ${purchase.purchaseState}, Products: ${purchase.products}")

        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                if (!purchase.isAcknowledged) {
                    acknowledgePurchase(purchase)
                }
                activateSubscription(isRestore)
            }
            Purchase.PurchaseState.PENDING -> {
                Log.d(TAG, "Purchase pending (payment processing)")
                _purchaseState.value = PurchaseState.Pending(
                    "Payment is being processed. This may take a few minutes."
                )
            }
            else -> {
                Log.w(TAG, "Purchase in unhandled state: ${purchase.purchaseState}")
            }
        }
    }

    /**
     * Acknowledge purchase with Google Play
     */
    private suspend fun acknowledgePurchase(purchase: Purchase) = withContext(Dispatchers.IO) {
        val client = billingClient ?: return@withContext

        try {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            val result = client.acknowledgePurchase(params)

            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Purchase acknowledged")
            } else {
                BillingErrorHandler.logError(
                    result.responseCode,
                    result.debugMessage,
                    "Acknowledge Purchase"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acknowledging purchase", e)
        }
    }

    /**
     * Activate subscription and grant premium access
     */
    private fun activateSubscription(isRestore: Boolean) {
        // Grant premium features via PremiumFeaturesManager
        PremiumFeaturesManager.updateSubscriptionStatus(true)

        _subscriptionState.value = SubscriptionState.Active

        if (isRestore) {
            _purchaseState.value = PurchaseState.Success("Subscription restored successfully")
        } else {
            _purchaseState.value = PurchaseState.Success("Premium features unlocked!")
        }

        Log.d(TAG, "Subscription activated")
    }

    /**
     * Launch purchase flow for subscription
     */
    fun launchPurchaseFlow(activity: Activity) {
        checkInitialized()
        val client = billingClient ?: return

        val details = _productDetails.value
        if (details == null) {
            Log.e(TAG, "Product details not available")
            _purchaseState.value = PurchaseState.Error("Product not available. Please try again.")
            return
        }

        // Get the offer with free trial (or base offer)
        val offerDetails = details.subscriptionOfferDetails?.firstOrNull()

        if (offerDetails == null) {
            Log.e(TAG, "No offer found for product")
            _purchaseState.value = PurchaseState.Error("Subscription offer not available")
            return
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .setOfferToken(offerDetails.offerToken)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        _purchaseState.value = PurchaseState.Processing

        val result = client.launchBillingFlow(activity, billingFlowParams)

        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            BillingErrorHandler.logError(
                result.responseCode,
                result.debugMessage,
                "Launch Purchase Flow"
            )
            _purchaseState.value = PurchaseState.Error(
                BillingErrorHandler.getUserMessage(result.responseCode)
            )
        }

        Log.d(TAG, "Purchase flow launched")
    }

    /**
     * Called when purchases are updated (new purchase, cancelled, etc.)
     */
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        Log.d(TAG, "onPurchasesUpdated - Response: ${billingResult.responseCode}")

        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases != null && purchases.isNotEmpty()) {
                    coroutineScope?.launch {
                        purchases.forEach { purchase ->
                            handlePurchase(purchase, isRestore = false)
                        }
                    }
                } else {
                    Log.w(TAG, "Purchase OK but list is empty")
                }
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "User cancelled purchase")
                _purchaseState.value = PurchaseState.Cancelled
            }

            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.NETWORK_ERROR -> {
                Log.w(TAG, "Network error during purchase")
                _purchaseState.value = PurchaseState.NetworkError(
                    BillingErrorHandler.getUserMessage(billingResult.responseCode)
                )
            }

            else -> {
                BillingErrorHandler.logError(
                    billingResult.responseCode,
                    billingResult.debugMessage,
                    "Purchase Update"
                )
                _purchaseState.value = PurchaseState.Error(
                    BillingErrorHandler.getUserMessage(billingResult.responseCode)
                )
            }
        }
    }

    /**
     * Restore purchases (re-query from Google Play)
     */
    suspend fun restorePurchases() {
        checkInitialized()
        Log.d(TAG, "Restoring purchases...")
        _purchaseState.value = PurchaseState.Processing

        queryPurchases()

        // Check if we found an active subscription
        delay(500) // Brief delay for processing
        if (_subscriptionState.value is SubscriptionState.Active) {
            _purchaseState.value = PurchaseState.Success("Subscription restored!")
        } else {
            _purchaseState.value = PurchaseState.Error("No active subscription found")
        }
    }

    /**
     * Get formatted price string
     * Returns price with trial info if available
     */
    fun getPrice(): String {
        val details = _productDetails.value ?: return "$0.99/month"

        val offerDetails = details.subscriptionOfferDetails?.firstOrNull()
        val pricingPhases = offerDetails?.pricingPhases?.pricingPhaseList ?: return "$0.99/month"

        // Get the recurring price (skip free trial phase)
        val recurringPhase = pricingPhases.find {
            it.priceAmountMicros > 0
        }

        return recurringPhase?.formattedPrice ?: "$0.99/month"
    }

    /**
     * Get trial info string
     * Returns trial period description if available
     */
    fun getTrialInfo(): String? {
        val details = _productDetails.value ?: return null

        val offerDetails = details.subscriptionOfferDetails?.firstOrNull()
        val pricingPhases = offerDetails?.pricingPhases?.pricingPhaseList ?: return null

        // Find free trial phase (price = 0)
        val trialPhase = pricingPhases.find {
            it.priceAmountMicros == 0L
        }

        return trialPhase?.let { phase ->
            // Parse billing period (e.g., "P3D" = 3 days, "P1W" = 1 week)
            val period = phase.billingPeriod
            when {
                period.contains("D") -> {
                    val days = period.replace("P", "").replace("D", "")
                    "$days days free"
                }
                period.contains("W") -> {
                    val weeks = period.replace("P", "").replace("W", "")
                    "$weeks week${if (weeks.toIntOrNull() ?: 1 > 1) "s" else ""} free"
                }
                period.contains("M") -> {
                    val months = period.replace("P", "").replace("M", "")
                    "$months month${if (months.toIntOrNull() ?: 1 > 1) "s" else ""} free"
                }
                else -> "Free trial"
            }
        }
    }

    /**
     * Get full pricing description
     * E.g., "3 days free, then $0.99/month"
     */
    fun getPricingDescription(): String {
        val trialInfo = getTrialInfo()
        val price = getPrice()

        return if (trialInfo != null) {
            "$trialInfo, then $price/month"
        } else {
            "$price/month"
        }
    }

    /**
     * Check if free trial is available
     */
    fun hasFreeTrial(): Boolean {
        return getTrialInfo() != null
    }

    /**
     * Reset purchase state to idle
     */
    fun resetPurchaseState() {
        _purchaseState.value = PurchaseState.Idle
    }

    /**
     * Check if user has premium access
     */
    fun hasPremiumAccess(): Boolean {
        return _subscriptionState.value is SubscriptionState.Active ||
               PremiumFeaturesManager.hasPremiumAccess()
    }

    /**
     * Cleanup
     */
    fun cleanup() {
        coroutineScope?.cancel()
        billingClient?.endConnection()
    }

    private fun checkInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("SubscriptionManager not initialized. Call init(context, productId) first.")
        }
    }
}

/**
 * Subscription state sealed class
 */
sealed class SubscriptionState {
    object Loading : SubscriptionState()
    object None : SubscriptionState()
    object Active : SubscriptionState()
    object Expired : SubscriptionState()
    data class Error(val message: String) : SubscriptionState()
}

/**
 * Purchase state sealed class
 */
sealed class PurchaseState {
    object Idle : PurchaseState()
    object Processing : PurchaseState()
    data class Success(val message: String) : PurchaseState()
    object Cancelled : PurchaseState()
    data class NetworkError(val message: String) : PurchaseState()
    data class Error(val message: String) : PurchaseState()
    data class Pending(val message: String) : PurchaseState()
}

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
 * Core billing logic for Google Play subscriptions with optional free trial.
 * Supports up to two parallel SKUs (monthly + yearly) — both are queried and
 * cached side-by-side, so the paywall can switch between plans without
 * reinitializing the billing client.
 *
 * Key responsibilities:
 * - Connect to Google Play Billing
 * - Query monthly + (optional) yearly product details in a single batch
 * - Handle purchase flow for the selected plan
 * - Acknowledge purchases and update [PremiumFeaturesManager]
 *
 * Backward compatibility:
 * - [init] (single-SKU) still works for apps that only ship a monthly plan.
 * - All no-arg accessors ([getPrice], [getTrialInfo], etc.) default to MONTHLY,
 *   matching pre-yearly behavior.
 */
object SubscriptionManager : PurchasesUpdatedListener {

    private const val TAG = "SubscriptionManager"
    private const val MAX_RETRY_ATTEMPTS = 3

    private var context: Context? = null
    private var monthlyProductId: String = ""
    private var yearlyProductId: String? = null
    private var billingClient: BillingClient? = null
    private var coroutineScope: CoroutineScope? = null

    // Product details cache — separate StateFlows for each SKU
    private val _monthlyProductDetails = MutableStateFlow<ProductDetails?>(null)
    val monthlyProductDetails: StateFlow<ProductDetails?> = _monthlyProductDetails.asStateFlow()

    private val _yearlyProductDetails = MutableStateFlow<ProductDetails?>(null)
    val yearlyProductDetails: StateFlow<ProductDetails?> = _yearlyProductDetails.asStateFlow()

    /**
     * Backward-compatible alias for [monthlyProductDetails]. New code should
     * read [monthlyProductDetails] or [yearlyProductDetails] directly.
     */
    @Deprecated(
        "Use monthlyProductDetails or yearlyProductDetails",
        ReplaceWith("monthlyProductDetails")
    )
    val productDetails: StateFlow<ProductDetails?> = monthlyProductDetails

    // Subscription state — unified across SKUs (single premium tier)
    private val _subscriptionState = MutableStateFlow<SubscriptionState>(SubscriptionState.Loading)
    val subscriptionState: StateFlow<SubscriptionState> = _subscriptionState.asStateFlow()

    // Which SKU is currently active (null when not subscribed)
    private val _activeSubscriptionType = MutableStateFlow<SubscriptionType?>(null)
    val activeSubscriptionType: StateFlow<SubscriptionType?> = _activeSubscriptionType.asStateFlow()

    // Purchase state
    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Idle)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()

    private var retryAttempt = 0
    private var isInitialized = false

    /**
     * Initialize with a single (monthly) product ID. Backward-compatible entry
     * point for apps that don't ship a yearly plan.
     *
     * @param context Application context
     * @param productId The monthly subscription product ID from Google Play Console
     */
    @Synchronized
    fun init(context: Context, productId: String) {
        init(context, monthlyProductId = productId, yearlyProductId = null)
    }

    /**
     * Initialize with both monthly and yearly product IDs. Both are queried
     * in a single batch call and can be purchased independently via
     * [launchPurchaseFlow].
     *
     * Calling this method a second time with new product IDs will re-query
     * product details without recreating the billing client.
     *
     * @param context Application context
     * @param monthlyProductId The monthly subscription product ID
     * @param yearlyProductId  The yearly subscription product ID, or null if
     *                         the app does not offer a yearly plan
     */
    @Synchronized
    fun init(context: Context, monthlyProductId: String, yearlyProductId: String?) {
        if (isInitialized) {
            // Allow apps to add a yearly SKU after initial init, or swap IDs
            val skuChanged = this.monthlyProductId != monthlyProductId ||
                    this.yearlyProductId != yearlyProductId
            if (!skuChanged) {
                Log.d(TAG, "SubscriptionManager already initialized with same product IDs")
                return
            }
            Log.d(TAG, "SubscriptionManager re-init: products changed, re-querying details")
            this.monthlyProductId = monthlyProductId
            this.yearlyProductId = yearlyProductId
            coroutineScope?.launch { queryProductDetails() }
            return
        }

        this.context = context.applicationContext
        this.monthlyProductId = monthlyProductId
        this.yearlyProductId = yearlyProductId
        this.coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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
        Log.d(
            TAG,
            "SubscriptionManager initialized — monthly=$monthlyProductId, yearly=${yearlyProductId ?: "<none>"}"
        )

        initializeBillingClient()
    }

    private fun initializeBillingClient() {
        Log.d(TAG, "Initializing billing client (attempt ${retryAttempt + 1})")
        connectToBillingService()
    }

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

    private fun onBillingServiceConnected() {
        coroutineScope?.launch {
            queryProductDetails()
            queryPurchases()
        }
    }

    /**
     * Query both monthly and yearly product details in a single batch call.
     */
    private suspend fun queryProductDetails() = withContext(Dispatchers.IO) {
        val client = billingClient ?: return@withContext

        try {
            val productList = mutableListOf<QueryProductDetailsParams.Product>()

            if (monthlyProductId.isNotEmpty()) {
                productList += QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(monthlyProductId)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            }
            yearlyProductId?.takeIf { it.isNotEmpty() }?.let { yearly ->
                productList += QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(yearly)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            }

            if (productList.isEmpty()) {
                Log.w(TAG, "No product IDs configured — skipping query")
                return@withContext
            }

            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build()

            val result = client.queryProductDetails(params)

            if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val detailsList = result.productDetailsList.orEmpty()
                detailsList.forEach { details ->
                    when (details.productId) {
                        monthlyProductId -> {
                            _monthlyProductDetails.value = details
                            Log.d(TAG, "Monthly product details loaded: ${details.productId}")
                            logOfferDetails(details, "MONTHLY")
                        }
                        yearlyProductId -> {
                            _yearlyProductDetails.value = details
                            Log.d(TAG, "Yearly product details loaded: ${details.productId}")
                            logOfferDetails(details, "YEARLY")
                        }
                    }
                }
                if (_monthlyProductDetails.value == null && monthlyProductId.isNotEmpty()) {
                    Log.w(TAG, "Monthly SKU not returned by Play: $monthlyProductId")
                }
                if (_yearlyProductDetails.value == null && yearlyProductId?.isNotEmpty() == true) {
                    Log.w(TAG, "Yearly SKU not returned by Play: $yearlyProductId")
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

    private fun logOfferDetails(details: ProductDetails, label: String) {
        details.subscriptionOfferDetails?.forEach { offer ->
            Log.d(TAG, "[$label] Offer: ${offer.offerId ?: "base"}")
            offer.pricingPhases.pricingPhaseList.forEach { phase ->
                Log.d(
                    TAG,
                    "  Phase: ${phase.formattedPrice}, " +
                            "Period: ${phase.billingPeriod}, " +
                            "Cycles: ${phase.billingCycleCount}"
                )
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

    private fun handleNoPurchases() {
        PremiumFeaturesManager.clearPremiumStatus()
        _activeSubscriptionType.value = null
        _subscriptionState.value = SubscriptionState.None
    }

    private suspend fun handlePurchase(purchase: Purchase, isRestore: Boolean) {
        Log.d(
            TAG,
            "Processing purchase — State: ${purchase.purchaseState}, Products: ${purchase.products}"
        )

        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                if (!purchase.isAcknowledged) {
                    acknowledgePurchase(purchase)
                }
                val type = subscriptionTypeFor(purchase)
                activateSubscription(isRestore, type)
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
     * Resolve which configured SKU a purchase corresponds to.
     */
    private fun subscriptionTypeFor(purchase: Purchase): SubscriptionType? {
        val purchasedId = purchase.products.firstOrNull() ?: return null
        return when (purchasedId) {
            monthlyProductId -> SubscriptionType.MONTHLY
            yearlyProductId -> SubscriptionType.YEARLY
            else -> null
        }
    }

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

    private fun activateSubscription(isRestore: Boolean, type: SubscriptionType?) {
        PremiumFeaturesManager.updateSubscriptionStatus(true)

        _activeSubscriptionType.value = type
        _subscriptionState.value = SubscriptionState.Active

        _purchaseState.value = if (isRestore) {
            PurchaseState.Success("Subscription restored successfully")
        } else {
            PurchaseState.Success("Premium features unlocked!")
        }

        Log.d(TAG, "Subscription activated — type=${type ?: "<unknown>"}")
    }

    /**
     * Launch purchase flow for the monthly subscription.
     * Backward-compatible alias for `launchPurchaseFlow(activity, MONTHLY)`.
     */
    fun launchPurchaseFlow(activity: Activity) {
        launchPurchaseFlow(activity, SubscriptionType.MONTHLY)
    }

    /**
     * Launch purchase flow for the chosen subscription type.
     */
    fun launchPurchaseFlow(activity: Activity, type: SubscriptionType) {
        checkInitialized()
        val client = billingClient ?: return

        val details = productDetailsFor(type)
        if (details == null) {
            Log.e(TAG, "Product details not available for $type")
            _purchaseState.value = PurchaseState.Error("Product not available. Please try again.")
            return
        }

        val offerDetails = details.subscriptionOfferDetails?.firstOrNull()
        if (offerDetails == null) {
            Log.e(TAG, "No offer found for product ${details.productId}")
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

        Log.d(TAG, "Purchase flow launched for $type")
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        Log.d(TAG, "onPurchasesUpdated — Response: ${billingResult.responseCode}")

        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (!purchases.isNullOrEmpty()) {
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

        delay(500) // Brief delay for processing
        if (_subscriptionState.value is SubscriptionState.Active) {
            _purchaseState.value = PurchaseState.Success("Subscription restored!")
        } else {
            _purchaseState.value = PurchaseState.Error("No active subscription found")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pricing accessors — type-aware overloads + backward-compatible aliases
    // ─────────────────────────────────────────────────────────────────────────

    /** Backward-compatible alias for `getPrice(MONTHLY)`. */
    fun getPrice(): String = getPrice(SubscriptionType.MONTHLY)

    /**
     * Get the formatted recurring price for the chosen plan.
     * Returns a sensible placeholder if product details are not yet loaded.
     */
    fun getPrice(type: SubscriptionType): String {
        val details = productDetailsFor(type) ?: return defaultPriceFor(type)
        val recurringPhase = details.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.find { it.priceAmountMicros > 0 }
        return recurringPhase?.formattedPrice ?: defaultPriceFor(type)
    }

    /** Backward-compatible alias for `getTrialInfo(MONTHLY)`. */
    fun getTrialInfo(): String? = getTrialInfo(SubscriptionType.MONTHLY)

    /**
     * Get a human-readable trial description for the chosen plan
     * (e.g. "3 days free"), or null if no trial is offered.
     */
    fun getTrialInfo(type: SubscriptionType): String? {
        val details = productDetailsFor(type) ?: return null
        val trialPhase = details.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.find { it.priceAmountMicros == 0L }
            ?: return null

        return formatTrialPeriod(trialPhase.billingPeriod)
    }

    /** Backward-compatible alias for `getPricingDescription(MONTHLY)`. */
    fun getPricingDescription(): String = getPricingDescription(SubscriptionType.MONTHLY)

    /**
     * Full pricing description, including trial if available.
     * Suffix is period-aware: "/month" for monthly, "/year" for yearly.
     */
    fun getPricingDescription(type: SubscriptionType): String {
        val trialInfo = getTrialInfo(type)
        val price = getPrice(type)
        val suffix = periodSuffixFor(type)

        return if (trialInfo != null) {
            "$trialInfo, then $price$suffix"
        } else {
            "$price$suffix"
        }
    }

    /** Backward-compatible alias for `hasFreeTrial(MONTHLY)`. */
    fun hasFreeTrial(): Boolean = hasFreeTrial(SubscriptionType.MONTHLY)

    /** Whether a free trial is offered for the chosen plan. */
    fun hasFreeTrial(type: SubscriptionType): Boolean = getTrialInfo(type) != null

    /**
     * Returns the cached [ProductDetails] for the chosen plan, or null if not
     * yet loaded (or yearly was never registered).
     */
    private fun productDetailsFor(type: SubscriptionType): ProductDetails? = when (type) {
        SubscriptionType.MONTHLY -> _monthlyProductDetails.value
        SubscriptionType.YEARLY -> _yearlyProductDetails.value
    }

    private fun defaultPriceFor(type: SubscriptionType): String = when (type) {
        SubscriptionType.MONTHLY -> "\$0.99/month"
        SubscriptionType.YEARLY -> "\$9.99/year"
    }

    private fun periodSuffixFor(type: SubscriptionType): String = when (type) {
        SubscriptionType.MONTHLY -> "/month"
        SubscriptionType.YEARLY -> "/year"
    }

    /**
     * Convert an ISO-8601 billing period (e.g. "P3D", "P1W", "P1M") into a
     * human-readable trial description.
     */
    private fun formatTrialPeriod(period: String): String {
        return when {
            period.contains("D") -> {
                val days = period.replace("P", "").replace("D", "")
                "$days days free"
            }
            period.contains("W") -> {
                val weeks = period.replace("P", "").replace("W", "")
                val plural = (weeks.toIntOrNull() ?: 1) > 1
                "$weeks week${if (plural) "s" else ""} free"
            }
            period.contains("M") -> {
                val months = period.replace("P", "").replace("M", "")
                val plural = (months.toIntOrNull() ?: 1) > 1
                "$months month${if (plural) "s" else ""} free"
            }
            else -> "Free trial"
        }
    }

    /** Reset purchase state to idle */
    fun resetPurchaseState() {
        _purchaseState.value = PurchaseState.Idle
    }

    /** Check if the user has premium access (either SKU). */
    fun hasPremiumAccess(): Boolean {
        return _subscriptionState.value is SubscriptionState.Active ||
                PremiumFeaturesManager.hasPremiumAccess()
    }

    /** Cleanup */
    fun cleanup() {
        coroutineScope?.cancel()
        billingClient?.endConnection()
    }

    private fun checkInitialized() {
        if (!isInitialized) {
            throw IllegalStateException(
                "SubscriptionManager not initialized. Call init(context, productId) " +
                        "or init(context, monthlyProductId, yearlyProductId) first."
            )
        }
    }
}

/**
 * Identifies which subscription plan an action targets.
 */
enum class SubscriptionType {
    MONTHLY,
    YEARLY
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

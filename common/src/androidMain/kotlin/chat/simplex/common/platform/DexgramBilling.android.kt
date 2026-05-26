package chat.simplex.common.platform

import com.android.billingclient.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private val SUBSCRIPTION_PRODUCT_IDS = listOf(
    "dexgram_1m",
    "dexgram_3m",
    "dexgram_6m",
    "dexgram_1y"
)

private val DURATION_LABELS = mapOf(
    "dexgram_1m" to "1 Month",
    "dexgram_3m" to "3 Months",
    "dexgram_6m" to "6 Months",
    "dexgram_1y" to "1 Year"
)

actual object DexgramBilling {

    actual val isAvailable: Boolean = true

    private var billingClient: BillingClient? = null
    private var pendingPurchaseResult: CompletableDeferred<DexgramPurchaseResult>? = null
    private val mutex = Mutex()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        val deferred = pendingPurchaseResult ?: return@PurchasesUpdatedListener
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val purchase = purchases?.firstOrNull()
                if (purchase != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        acknowledgePurchase(purchase)
                    }
                    deferred.complete(
                        DexgramPurchaseResult(
                            success = true,
                            productId = purchase.products.firstOrNull() ?: "",
                            purchaseToken = purchase.purchaseToken
                        )
                    )
                } else {
                    deferred.complete(DexgramPurchaseResult(success = false, errorMessage = "No purchase data"))
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                deferred.complete(DexgramPurchaseResult(success = false, errorMessage = "Purchase cancelled"))
            }
            else -> {
                deferred.complete(
                    DexgramPurchaseResult(
                        success = false,
                        errorMessage = "Purchase failed (code ${billingResult.responseCode})"
                    )
                )
            }
        }
        pendingPurchaseResult = null
    }

    actual suspend fun connect(): Boolean = suspendCoroutine { cont ->
        val activity = mainActivity.get()
        if (activity == null) {
            cont.resume(false)
            return@suspendCoroutine
        }

        val client = BillingClient.newBuilder(activity)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

        billingClient = client

        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
            }

            override fun onBillingServiceDisconnected() {
                // Reconnection handled on next user action
            }
        })
    }

    actual suspend fun queryProducts(): List<DexgramProduct> = withContext(Dispatchers.IO) {
        val client = billingClient ?: return@withContext emptyList()
        if (!client.isReady) return@withContext emptyList()

        val productList = SUBSCRIPTION_PRODUCT_IDS.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        val result = suspendCoroutine<Pair<BillingResult, List<ProductDetails>>> { cont ->
            client.queryProductDetailsAsync(params) { billingResult, detailsList ->
                cont.resume(billingResult to detailsList)
            }
        }

        if (result.first.responseCode != BillingClient.BillingResponseCode.OK) {
            return@withContext emptyList()
        }

        result.second.mapNotNull { details ->
            val offer = details.subscriptionOfferDetails?.firstOrNull() ?: return@mapNotNull null
            val pricingPhase = offer.pricingPhases.pricingPhaseList.firstOrNull() ?: return@mapNotNull null
            DexgramProduct(
                productId = details.productId,
                title = details.name,
                price = pricingPhase.formattedPrice,
                durationLabel = DURATION_LABELS[details.productId] ?: details.name
            )
        }.sortedBy { SUBSCRIPTION_PRODUCT_IDS.indexOf(it.productId) }
    }

    actual suspend fun purchase(productId: String): DexgramPurchaseResult = mutex.withLock {
        val client = billingClient
        val activity = mainActivity.get()

        if (client == null || !client.isReady || activity == null) {
            return DexgramPurchaseResult(success = false, errorMessage = "Billing not ready")
        }

        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val queryParams = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        val queryResult = suspendCoroutine<Pair<BillingResult, List<ProductDetails>>> { cont ->
            client.queryProductDetailsAsync(queryParams) { billingResult, detailsList ->
                cont.resume(billingResult to detailsList)
            }
        }

        val productDetails = queryResult.second.firstOrNull()
            ?: return DexgramPurchaseResult(success = false, errorMessage = "Product not found")

        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
            ?: return DexgramPurchaseResult(success = false, errorMessage = "No offer available")

        val deferred = CompletableDeferred<DexgramPurchaseResult>()
        pendingPurchaseResult = deferred

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()

        val launchResult = withContext(Dispatchers.Main) {
            client.launchBillingFlow(activity, flowParams)
        }

        if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
            pendingPurchaseResult = null
            return DexgramPurchaseResult(
                success = false,
                errorMessage = "Could not launch purchase (code ${launchResult.responseCode})"
            )
        }

        return deferred.await()
    }

    actual fun disconnect() {
        billingClient?.endConnection()
        billingClient = null
        pendingPurchaseResult = null
    }

    private suspend fun acknowledgePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            val client = billingClient ?: return
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            withContext(Dispatchers.IO) {
                suspendCoroutine<BillingResult> { cont ->
                    client.acknowledgePurchase(params) { result ->
                        cont.resume(result)
                    }
                }
            }
        }
    }
}

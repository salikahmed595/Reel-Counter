package com.reelcounter.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.reelcounter.app.core.Clock
import com.reelcounter.app.data.prefs.EntitlementCache
import com.reelcounter.app.data.prefs.EntitlementState
import com.reelcounter.app.data.prefs.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlanInfo(val productId: String, val formattedPrice: String?, val billingPeriod: String?, val offerToken: String?, val details: ProductDetails?)

enum class BillingStatus { IDLE, CONNECTING, READY, UNAVAILABLE, PENDING, VERIFYING, VERIFICATION_UNAVAILABLE, PURCHASED, ERROR }

/**
 * Play Billing wrapper (PRD §11). Purchases unlock Pro only after server verification; pending
 * purchases never unlock. Activity data is never involved.
 */
class BillingManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val prefsRepo: PreferencesRepository,
    private val verifier: EntitlementVerifier,
    private val clock: Clock,
) : PurchasesUpdatedListener {
    private val _plans = MutableStateFlow<Map<String, PlanInfo>>(emptyMap())
    val plans: StateFlow<Map<String, PlanInfo>> = _plans.asStateFlow()

    private val _status = MutableStateFlow(BillingStatus.IDLE)
    val status: StateFlow<BillingStatus> = _status.asStateFlow()

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun connect() {
        if (client.isReady) {
            refresh()
            return
        }
        _status.value = BillingStatus.CONNECTING
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _status.value = BillingStatus.READY
                    refresh()
                } else {
                    _status.value = BillingStatus.UNAVAILABLE
                }
            }

            override fun onBillingServiceDisconnected() {
                _status.value = BillingStatus.UNAVAILABLE
            }
        })
    }

    private fun refresh() {
        queryProducts()
        restore()
    }

    private fun queryProducts() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                ProductIds.ALL.map {
                    QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.SUBS).build()
                },
            ).build()
        client.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            _plans.value = details.productDetailsList.associate { pd ->
                // Base plan only (no offerId): no confusing trials or intro offers by default.
                val offer = pd.subscriptionOfferDetails?.firstOrNull { it.offerId == null } ?: pd.subscriptionOfferDetails?.firstOrNull()
                val phase = offer?.pricingPhases?.pricingPhaseList?.lastOrNull()
                pd.productId to PlanInfo(pd.productId, phase?.formattedPrice, phase?.billingPeriod, offer?.offerToken, pd)
            }
        }
    }

    /** Restore / reconcile: re-reads owned subscriptions from Play (works after reinstall). */
    fun restore() {
        if (!client.isReady) {
            connect()
            return
        }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) scope.launch { handle(purchases, fromRestore = true) }
        }
    }

    fun launchPurchase(activity: Activity, productId: String): Boolean {
        val plan = _plans.value[productId] ?: return false
        val details = plan.details ?: return false
        val token = plan.offerToken ?: return false
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).setOfferToken(token).build()),
            ).build()
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> scope.launch { handle(purchases.orEmpty(), fromRestore = false) }
            BillingClient.BillingResponseCode.USER_CANCELED -> _status.value = BillingStatus.READY
            else -> _status.value = BillingStatus.ERROR
        }
    }

    private suspend fun handle(purchases: List<Purchase>, fromRestore: Boolean) {
        val cached = prefsRepo.current().entitlement
        val relevant = purchases.filter { p -> p.products.any { it in ProductIds.ALL } }
        if (relevant.isEmpty()) {
            if (fromRestore && (cached.state == EntitlementState.ACTIVE || cached.state == EntitlementState.GRACE || cached.state == EntitlementState.PENDING)) {
                prefsRepo.setEntitlement(cached.copy(state = EntitlementState.EXPIRED, lastVerifiedAtMs = clock.nowMs()))
            }
            if (_status.value != BillingStatus.UNAVAILABLE) _status.value = BillingStatus.READY
            return
        }
        for (p in relevant) {
            val productId = p.products.first { it in ProductIds.ALL }
            when (p.purchaseState) {
                Purchase.PurchaseState.PENDING -> {
                    prefsRepo.setEntitlement(EntitlementCache(EntitlementState.PENDING, productId, cached.lastVerifiedAtMs, 0))
                    _status.value = BillingStatus.PENDING
                }
                Purchase.PurchaseState.PURCHASED -> {
                    _status.value = BillingStatus.VERIFYING
                    when (val v = verifier.verify(context.packageName, productId, p.purchaseToken)) {
                        is VerificationResult.Verified -> {
                            prefsRepo.setEntitlement(EntitlementCache(v.state, productId, clock.nowMs(), v.expiresAtMs))
                            if (!p.isAcknowledged && !v.serverAcknowledged && v.state == EntitlementState.ACTIVE) {
                                client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { }
                            }
                            _status.value = if (v.state == EntitlementState.ACTIVE || v.state == EntitlementState.GRACE) BillingStatus.PURCHASED else BillingStatus.READY
                        }
                        VerificationResult.Rejected -> {
                            prefsRepo.setEntitlement(EntitlementCache(EntitlementState.EXPIRED, productId, clock.nowMs(), 0))
                            _status.value = BillingStatus.READY
                        }
                        is VerificationResult.Unavailable -> {
                            // Keep a previously verified entitlement under the capped grace window;
                            // otherwise stay locked until verification succeeds.
                            if (cached.state != EntitlementState.ACTIVE && cached.state != EntitlementState.GRACE) {
                                prefsRepo.setEntitlement(EntitlementCache(EntitlementState.UNVERIFIED, productId, cached.lastVerifiedAtMs, 0))
                            }
                            _status.value = BillingStatus.VERIFICATION_UNAVAILABLE
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    companion object {
        fun manageSubscriptionUrl(packageName: String, productId: String?): String =
            if (productId == null) "https://play.google.com/store/account/subscriptions"
            else "https://play.google.com/store/account/subscriptions?sku=$productId&package=$packageName"
    }
}

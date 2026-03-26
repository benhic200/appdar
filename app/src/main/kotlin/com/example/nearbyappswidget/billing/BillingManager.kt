package com.example.nearbyappswidget.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*

private const val TAG = "BillingManager"

/** Play Store product ID — must match exactly what you create in Play Console. */
const val PRODUCT_ID = "appdar_pro"

/**
 * Wraps all Google Play Billing logic.
 *
 * Usage:
 *  1. Create in Activity.onCreate
 *  2. Call startConnection() — this also restores existing purchases on startup
 *  3. Call launchPurchaseFlow(activity) when the user taps an upgrade CTA
 *  4. Call destroy() in Activity.onDestroy
 */
class BillingManager(
    private val context: Context,
    private val onProUnlocked: () -> Unit,
    private val onPurchaseFailed: (String) -> Unit
) : PurchasesUpdatedListener {

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    /** Stored when launchPurchaseFlow is called before billing is ready; retried on connect. */
    private var pendingPurchaseActivity: Activity? = null

    // ── Connection ────────────────────────────────────────────────────────────

    fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing connected — checking existing purchases")
                    checkExistingPurchases()
                    // Retry a pending purchase if one was queued before connection completed
                    pendingPurchaseActivity?.let { activity ->
                        pendingPurchaseActivity = null
                        launchPurchaseFlow(activity)
                    }
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                    pendingPurchaseActivity = null
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
            }
        })
    }

    // ── Restore existing purchase (reinstall / new device) ────────────────────

    fun checkExistingPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            for (purchase in purchases) {
                if (purchase.products.contains(PRODUCT_ID) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    Log.d(TAG, "Existing Pro purchase found")
                    acknowledgePurchase(purchase)
                    onProUnlocked()
                }
            }
        }
    }

    // ── Launch purchase sheet ─────────────────────────────────────────────────

    fun launchPurchaseFlow(activity: Activity) {
        if (!billingClient.isReady) {
            Log.d(TAG, "Billing not ready — queuing purchase and reconnecting")
            pendingPurchaseActivity = activity
            startConnection()
            return
        }
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(productList).build()
        ) { result, productDetailsList ->
            if (productDetailsList.isEmpty()) {
                Log.e(TAG, "No product details for $PRODUCT_ID — check Play Console: " +
                    "product must be created, active, and app published to at least Internal Testing")
                onPurchaseFailed("Purchase unavailable — please try again later")
                return@queryProductDetailsAsync
            }
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetailsList.first())
                            .build()
                    )
                )
                .build()
            // launchBillingFlow must run on the main thread
            activity.runOnUiThread {
                billingClient.launchBillingFlow(activity, flowParams)
            }
        }
    }

    // ── Purchase result callback ──────────────────────────────────────────────

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        Log.d(TAG, "Purchase completed: ${purchase.products}")
                        acknowledgePurchase(purchase)
                        onProUnlocked()
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED ->
                Log.d(TAG, "Purchase cancelled by user")
            else -> {
                Log.e(TAG, "Purchase failed: ${result.debugMessage}")
                onPurchaseFailed("Purchase failed — please try again")
            }
        }
    }

    // ── Acknowledge (required within 3 days or Google auto-refunds) ───────────

    private fun acknowledgePurchase(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            Log.d(TAG, "Acknowledge result: ${result.responseCode}")
        }
    }

    fun destroy() {
        billingClient.endConnection()
    }
}

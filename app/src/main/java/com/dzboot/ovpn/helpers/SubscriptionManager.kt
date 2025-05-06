package com.dzboot.ovpn.helpers

import androidx.appcompat.app.AppCompatActivity
import com.android.billingclient.api.*
import com.dzboot.ovpn.BuildConfig
import com.dzboot.ovpn.base.BaseApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber


class SubscriptionManager : BillingClientStateListener, PurchasesUpdatedListener {

    companion object {

        const val YEARLY_SUBSCRIPTION = "no_ads_yearly"
        const val SEMESTERLY_SUBSCRIPTION = "no_ads_semesterly"
        const val MONTHLY_SUBSCRIPTION = "no_ads_monthly"

        // For Singleton instantiation
        @Volatile
        private var instance: SubscriptionManager? = null

        //initialize
        fun init(callback: InitializationCallback) = with(getInstance()) {
            if (!isClientReady()) {
                Timber.d("Starting BillingClient connection")
                initCallback = callback
                client.startConnection(this)
            }
        }

        fun getInstance(): SubscriptionManager {
            return instance ?: synchronized(this) { SubscriptionManager().also { instance = it } }
        }
    }

    lateinit var initCallback: InitializationCallback
    private var purchaseCallback: PurchaseFlowCallback? = null
    private var doNext: Runnable? = null
    var isSubscribed = BuildConfig.ALWAYS_SUBSCRIBED
    val client: BillingClient by lazy {
        BillingClient.newBuilder(BaseApplication.instance).setListener(this).enablePendingPurchases().build()
    }


    fun isClientReady() = client.isReady

    fun setInitializationCallback(initializationCallback: InitializationCallback): SubscriptionManager {
        initCallback = initializationCallback
        return this
    }

    fun isFeatureSupported() =
        client.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS)
            .responseCode != BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED

    override fun onBillingSetupFinished(result: BillingResult) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (doNext == null) {
                    Timber.d("Billing init success (default callback)")
                    initCallback.onInitSuccess()
                } else {
                    Timber.d("Billing init success (nextStep)")
                    doNext?.run()
                }
            }
            else -> initCallback.onInitFail(result)
        }
    }

    fun queryPurchases(onCheckResult: (subscribed: Boolean) -> Unit) =
        client.queryPurchasesAsync(BillingClient.SkuType.SUBS) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK)
                checkSubscription(purchases, onCheckResult)
            else {
                onCheckResult(false)
                disablePremiumFeatures()
            }
        }

    private fun disablePremiumFeatures() {
        if (!BuildConfig.ALWAYS_SUBSCRIBED)
            isSubscribed = false

        ShortcutsHelper.removeAllShortcuts()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Timber.w("Purchase flow fail $this")
            purchaseCallback?.onPurchaseFail(result.responseCode)
            return
        }

        checkSubscription(purchases, null)
    }

    private fun checkSubscription(purchases: MutableList<Purchase>?, onCheckResult: ((subscribed: Boolean) -> Unit)?) {
        if (purchases.isNullOrEmpty()) {
            Timber.w("Check subscription : No purchases")
            disablePremiumFeatures()

            if (onCheckResult == null)
                purchaseCallback?.onPurchaseNone()
            else
                onCheckResult(false)
            return
        }

        val activePurchases = arrayListOf<Purchase>()
        for (purchase in purchases) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                if (checkPurchaseValidity(purchase))
                    activePurchases.add(purchase)
                acknowledgePurchase(purchase)
            }
        }

        if (activePurchases.isEmpty()) {
            Timber.i("Purchase flow fail : No valid purchases")
            disablePremiumFeatures()

            if (onCheckResult == null)
                purchaseCallback?.onPurchaseNone()
            else
                onCheckResult(false)
        } else {
            Timber.i("Purchase flow success size: ${activePurchases.size}")
            isSubscribed = true
            if (onCheckResult == null)
                purchaseCallback?.onPurchaseSuccess(activePurchases)
            else
                onCheckResult(true)
        }
    }

    private fun checkPurchaseValidity(purchase: Purchase): Boolean {
        val productId = purchase.skus
        val time = purchase.purchaseTime
        Timber.d(purchase.toString())
        return true
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val ackParams = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken)
            CoroutineScope(Dispatchers.IO).launch { client.acknowledgePurchase(ackParams.build()) }
        }
    }

    override fun onBillingServiceDisconnected() = client.startConnection(this)

    fun querySkuDetails(callback: QuerySkuDetailsCallback) {
        if (client.isReady) {
            val skuList = ArrayList<String>()
            skuList.add(MONTHLY_SUBSCRIPTION)
            skuList.add(SEMESTERLY_SUBSCRIPTION)
            skuList.add(YEARLY_SUBSCRIPTION)
            val params = SkuDetailsParams.newBuilder()
            params.setSkusList(skuList).setType(BillingClient.SkuType.SUBS)

            CoroutineScope(Dispatchers.IO).launch {
                val skuResult = client.querySkuDetails(params.build())

                withContext(Dispatchers.Main) {
                    if (skuResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        with(skuResult.skuDetailsList) {
                            if (isNullOrEmpty())
                                callback.onInvalidSkuResult()
                            else
                                callback.onSkuResultsOK(this)
                        }
                    } else {
                        callback.onSkuResultsError(skuResult.billingResult)
                    }
                }
            }
        } else {
            doNext = Runnable { querySkuDetails(callback) }
            client.startConnection(this)
        }
    }

    fun launchPurchaseFlow(
        activity: AppCompatActivity,
        skuDetails: SkuDetails,
        purchaseFlowCallback: PurchaseFlowCallback
    ) {
        this.purchaseCallback = purchaseFlowCallback

        if (client.isReady) {
            val flowParams = BillingFlowParams.newBuilder().setSkuDetails(skuDetails).build()
            client.launchBillingFlow(activity, flowParams)
        } else {
            doNext = Runnable { launchPurchaseFlow(activity, skuDetails, purchaseFlowCallback) }
            client.startConnection(this)
        }
    }

    abstract class InitializationCallback {

        open fun onInitFail(result: BillingResult) {
            Timber.e("Billing init error: ${result.debugMessage}")
        }

        abstract fun onInitSuccess()
    }

    abstract class QuerySkuDetailsCallback {

        abstract fun onSkuResultsOK(skuDetailsList: List<SkuDetails>)
        open fun onInvalidSkuResult() = Timber.e("Sku list is null or empty")
        open fun onSkuResultsError(result: BillingResult) = Timber.e(result.debugMessage)
    }

    abstract class PurchaseFlowCallback {

        abstract fun onPurchaseSuccess(purchases: List<Purchase>)
        abstract fun onPurchaseNone()
        abstract fun onPurchaseFail(@BillingClient.BillingResponseCode responseCode: Int)
    }
}
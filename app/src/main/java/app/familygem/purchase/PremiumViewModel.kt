package app.familygem.purchase

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.familygem.BuildConfig
import app.familygem.Global
import app.familygem.R
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.Purchase.PurchaseState
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Business manager of [PremiumFragment].
 */
class PremiumViewModel(application: Application) : AndroidViewModel(application), PurchasesUpdatedListener {

    companion object {
        const val PRODUCT_ID = "family_gem_premium"
    }

    enum class Show { ADVERTISEMENT, ACTIVATED }
    enum class Status { REACTIVATE, ERROR }

    private lateinit var billingClient: BillingClient
    val show = MutableLiveData<Show>() // What to show: the advertisement or the activated banner
    val status = MutableLiveData<Status>()
    val message = MutableLiveData<Any>() // Int or String to be displayed in Toast
    val productDetails = MutableLiveData<ProductDetails?>() // Product found on Google Play server
    val purchaseToken = MutableLiveData<String?>() // The purchase token that can be consumed

    private fun setMessage(message: Any) {
        this.message.postValue(message) // postValue() is good for both Main and IO Dispatchers
    }

    init {
        connectGoogleBilling(getApplication<Application>().applicationContext)
    }

    /**
     * Creates the billing client and establishes connection with Google Play billing system.
     */
    fun connectGoogleBilling(context: Context) {
        billingClient = BillingClient.newBuilder(context).setListener(this).enablePendingPurchases().build()
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                when (billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        queryHistory() // Query purchases from the past
                        viewModelScope.launch(IO) { queryProducts() } // To display the product and buy it
                    }
                    // BILLING_UNAVAILABLE, Debug Message: Google Play In-app Billing API version is less than 3
                    BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                        status.postValue(Status.ERROR)
                        setMessage(R.string.update_playstore)
                    }
                    else -> { // Other error
                        status.postValue(Status.ERROR)
                        setMessage(billingResult.debugMessage)
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                // Tries to restart the connection to Google Play
                connectGoogleBilling(context)
            }
        })
    }

    /**
     * Checks if the product was already purchased to immediately activate Premium or make it available to buy.
     */
    private fun queryHistory() {
        // Returns purchases details for currently owned items bought within the app
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        // Be careful: queryPurchasesAsync() often gets purchases from device cache, not always from the web! So may be not reliable.
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                if (purchases.isNotEmpty() && purchases[0].purchaseState == PurchaseState.PURCHASED) {
                    // Premium was already been bought
                    viewModelScope.launch(IO) { verifyPurchase(purchases[0]) }
                } else if (purchases.isEmpty()) {
                    // Premium consumed maybe on another device: needs to be deactivated here too
                    completeConsumption()
                }
            } else { // All other errors
                status.postValue(Status.ERROR)
                setMessage(R.string.something_wrong)
            }
        }
    }

    /**
     * Gets product details from Google Play to display it on layout.
     */
    suspend fun queryProducts() {
        // Old versions of Google Play app give the error FEATURE_NOT_SUPPORTED: billing client does not support ProductDetails
        val billingResult = billingClient.isFeatureSupported(BillingClient.FeatureType.PRODUCT_DETAILS)
        if (billingResult.responseCode == BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED) {
            setMessage(R.string.update_playstore)
            return
        }
        val products = listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build()
        )
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        val productDetailsResult = billingClient.queryProductDetails(params)
        val response = productDetailsResult.billingResult.responseCode
        if (response == BillingClient.BillingResponseCode.OK && !productDetailsResult.productDetailsList.isNullOrEmpty()) {
            // Displays product details on layout
            productDetails.postValue(productDetailsResult.productDetailsList!![0])
        } else if (productDetailsResult.productDetailsList.isNullOrEmpty()
            || response == BillingClient.BillingResponseCode.ERROR || response == BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE
        ) {
            // Can't retrieve products, maybe for network error
            status.postValue(Status.ERROR)
            setMessage(R.string.something_wrong)
        }
    }

    /**
     * Launches billing flow if client is connected, reconnecting if necessary.
     * To be called fom the fragment.
     */
    fun buyPremium(activity: Activity) {
        val state = billingClient.connectionState
        if (state == BillingClient.ConnectionState.CONNECTED) {
            launchFlow(activity)
        } else if (state == BillingClient.ConnectionState.DISCONNECTED || state == BillingClient.ConnectionState.CLOSED) {
            setMessage(R.string.something_wrong)
            connectGoogleBilling(activity)
        }
    }

    /**
     * Listener called after the purchase is made.
     */
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && !purchases.isNullOrEmpty()) {
            // Product just purchased
            for (purchase in purchases) {
                if (purchase.purchaseState == PurchaseState.PURCHASED) {
                    viewModelScope.launch(IO) { verifyPurchase(purchase) }
                }
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            // In this case Purchases is null, so we have to trust Google without verifying the purchase on our server
            becomePremium()
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            // User clicks outside the Google Play dialog box
            status.postValue(Status.REACTIVATE)
        } else {
            // User not signed into Google Account
            // Or NETWORK_ERROR (no internet connection)
            status.postValue(Status.REACTIVATE)
            setMessage(R.string.something_wrong)
        }
    }

    /** Launches the purchase flow.
     *  @param activity Reference from which the billing flow will be launched
     */
    private fun launchFlow(activity: Activity) {
        if (productDetails.value == null) return
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(productDetails.value!!).build()
        )
        val billingFlowParams = BillingFlowParams.newBuilder().setProductDetailsParamsList(productDetailsParamsList).build()
        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    /**
     * Checks on the backend server if the purchase has been already done or not, if not acknowledges it.
     */
    private fun verifyPurchase(purchase: Purchase) {
        try {
            val url = URL("https://www.familygem.app/purchase.php")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            val stream = connection.outputStream
            val query = "passKey=" + URLEncoder.encode(BuildConfig.PASS_KEY, "UTF-8") +
                    "&orderId=" + purchase.orderId +
                    "&purchaseTime=" + purchase.purchaseTime +
                    "&purchaseToken=" + purchase.purchaseToken
            stream.write(query.toByteArray(StandardCharsets.UTF_8))
            stream.flush()
            stream.close()
            // Answer
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val line = reader.readLine()
            reader.close()
            connection.disconnect()
            // Purchase was already consumed, maybe on another device
            if (line == "consumed") {
                completeConsumption()
            } // Purchase just inserted on backend database
            else if (line == purchase.purchaseTime.toString() && !purchase.isAcknowledged) {
                // Acknowledges purchase
                val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                billingClient.acknowledgePurchase(params) { billingResult: BillingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        becomePremium()
                        purchaseToken.postValue(purchase.purchaseToken)
                    } else {
                        setMessage(R.string.something_wrong)
                    }
                }
            } // Premium already purchased in the past
            else if (line == purchase.purchaseToken && purchase.isAcknowledged) {
                becomePremium()
                purchaseToken.postValue(purchase.purchaseToken)
            } else {
                setMessage(R.string.something_wrong)
            }
        } catch (exception: Exception) {
            // E.g. no internet connection
            setMessage(exception.localizedMessage)
        }
    }

    /**
     * The final goal of the entire purchase process.
     * Can be called after the purchase flow or because the Premium product results already been bought on Google Play.
     */
    private fun becomePremium() {
        if (!Global.settings.premium) {
            Global.settings.premium = true
            Global.settings.save()
            show.postValue(Show.ACTIVATED)
            setMessage(R.string.premium_activated)
        }
    }

    /**
     * Consume the purchase with the token retrieved by [PremiumViewModel.verifyPurchase].
     */
    fun consumePurchase() {
        if (purchaseToken.value != null) {
            viewModelScope.launch(IO) {
                val consumeParams = ConsumeParams.newBuilder().setPurchaseToken(purchaseToken.value!!).build()
                val consumeResult = billingClient.consumePurchase(consumeParams)
                if (consumeResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    if (BuildConfig.PASS_KEY.isNotEmpty()) registerConsumedPurchase(consumeResult.purchaseToken!!)
                } else if (consumeResult.billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_NOT_OWNED) {
                    setMessage(consumeResult.billingResult.debugMessage)
                    completeConsumption()
                }
            }
        } else setMessage(R.string.something_wrong)
    }

    /**
     * Registers on the backend server the consumption of the purchase with this token.
     */
    private fun registerConsumedPurchase(purchaseToken: String) {
        try {
            val url = URL("https://www.familygem.app/consume.php")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            val stream = connection.outputStream
            val query = "passKey=" + URLEncoder.encode(BuildConfig.PASS_KEY, "UTF-8") +
                    "&purchaseToken=" + purchaseToken
            stream.write(query.toByteArray(StandardCharsets.UTF_8))
            stream.flush()
            stream.close()
            // Answer
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val line = reader.readLine()
            reader.close()
            connection.disconnect()
            if (line == purchaseToken) {
                completeConsumption()
            } else setMessage(line)
        } catch (exception: Exception) {
            // E.g. no internet connection
            setMessage(exception.localizedMessage)
        }
    }

    private fun completeConsumption() {
        if (Global.settings.premium) {
            Global.settings.premium = false
            Global.settings.save()
            show.postValue(Show.ADVERTISEMENT)
            setMessage(R.string.premium_deactivated)
        }
    }

    fun finish() {
        if (billingClient.isReady) billingClient.endConnection()
    }
}

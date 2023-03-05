package app.familygem;

import static graph.gedcom.Util.p;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import app.familygem.constant.Extra;

/**
 * Here you can buy Family Gem Premium from Google Play.
 */
public class PurchaseActivity extends AppCompatActivity {

    private BillingClient billingClient;
    private static final String PRODUCT_ID = "family_gem_premium";
    List<ProductDetails> productDetails; // Product(s) found on Google Play server

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.purchase_activity);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        int titleId = getIntent().getIntExtra(Extra.STRING, 0);
        setTitle(titleId);
        TextView moreAbout = findViewById(R.id.purchase_more);
        moreAbout.setOnClickListener(view -> startActivity(
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.familygem.app/premium"))));
        // Product box
        findViewById(R.id.purchase_product).setOnClickListener(view -> clickButton());
        // Purchase button
        findViewById(R.id.purchase_button).setOnClickListener(view -> clickButton());
        // Starts the purchase process
        connectGoogleBilling();
    }

    /**
     * Launches billing flow if client is connected, reconnecting if necessary.
     */
    private void clickButton() {
        p("Connection state:", billingClient.getConnectionState());
        int state = billingClient.getConnectionState();
        if (state == BillingClient.ConnectionState.CONNECTED) {
            launchFlow();
        } else if (state == BillingClient.ConnectionState.DISCONNECTED
                || state == BillingClient.ConnectionState.CLOSED) {
            connectGoogleBilling();
        }
    }

    /**
     * Creates the billing client and establishes connection with Google Play billing system.
     */
    private void connectGoogleBilling() {
        PurchasesUpdatedListener purchasesUpdatedListener = (billingResult, purchases) -> {
            // At first test this was called twice. Maybe because before I cancelled the flow clicking outside Google Play dialog?
            p("On purchases updated:", billingResult);
            p("Purchases:", purchases);
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                // Product just purchased
                for (Purchase purchase : purchases) {
                    if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                        verifyPurchase(purchase);
                    }
                }
            } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                // In this case Purchases is null, so we have to trust Google without verifying the purchase on our server
                becomePremium();
            } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
                // User clicks outside the Google Play dialog box
                p("User cancelled flow");
            } else {
                // User not signed in to Google Account
                p("Other error");
            }
        };
        // Creates client instance, necessary if already created and connection ended
        billingClient = BillingClient.newBuilder(this).enablePendingPurchases()
                .setListener(purchasesUpdatedListener).build();
        // Starts connection
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                p("Billing setup finished:", billingResult);
                // The BillingClient is ready
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    if (productDetails != null && !productDetails.isEmpty()) // The Premium product is already visible and we can buy it
                        launchFlow();
                    else // Query purchases from the past
                        queryHistory();
                } // BILLING_UNAVAILABLE, Debug Message: Google Play In-app Billing API version is less than 3
                else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE) {
                    U.toast(PurchaseActivity.this, R.string.update_googleplay);
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to Google Play
                p("Billing service disconnected");
                connectGoogleBilling();
            }
        });
    }

    /**
     * Checks if the product was already purchased to immediately activate Premium.
     */
    private void queryHistory() {
        // Returns purchases details for currently owned items bought within your app
        QueryPurchasesParams queryPurchasesParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP).build();
        billingClient.queryPurchasesAsync(queryPurchasesParams, (billingResult, purchases) -> {
            p("On query purchases:", billingResult);
            p("Purchases:", purchases);
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                if (!purchases.isEmpty() && purchases.get(0).getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    // Premium was already been bought
                    verifyPurchase(purchases.get(0));
                } else {
                    queryProducts(); // To display the product and buy it
                }
            } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.ERROR) {
                U.toast(this, R.string.something_wrong);
            } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.DEVELOPER_ERROR) {
                // Handle the developer error response.
            } else {
                // Other error codes are available, but are never returned by the Appstore SDK.
            }
        });
    }

    /**
     * Gets product details from Google Play to display it on layout.
     */
    private void queryProducts() {
        // Old versions of Google Play app give the error FEATURE_NOT_SUPPORTED: billing client does not support ProductDetails
        BillingResult billingResult = billingClient.isFeatureSupported(BillingClient.FeatureType.PRODUCT_DETAILS);
        p("Is product details supported:", billingResult);
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED) {
            U.toast(this, R.string.update_googleplay);
            return;
        }
        List<QueryProductDetailsParams.Product> products = new ArrayList<>();
        products.add(QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP).build());
        QueryProductDetailsParams queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
                .setProductList(products).build();
        billingClient.queryProductDetailsAsync(queryProductDetailsParams, (billingResult1, productDetailsList) -> {
            p("On product details:", billingResult1);
            p("Product details list:", productDetailsList);
            if (billingResult1.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                // Displays product details on layout
                if (!productDetailsList.isEmpty()) {
                    ProductDetails details = productDetailsList.get(0);
                    runOnUiThread(() -> {
                        ((TextView)findViewById(R.id.purchase_product_title)).setText(details.getName());
                        ((TextView)findViewById(R.id.purchase_product_description)).setText(
                                details.getDescription().replace("\n", "").replace(".", ".\n"));
                        ((TextView)findViewById(R.id.purchase_product_price)).setText(
                                details.getOneTimePurchaseOfferDetails().getFormattedPrice() + '/');
                        findViewById(R.id.purchase_product).setVisibility(View.VISIBLE);
                        findViewById(R.id.purchase_button).setEnabled(true);
                    });
                } else { // Product details not found
                    U.toast(this, R.string.something_wrong);
                }
                productDetails = productDetailsList;
            }
        });
    }

    /**
     * The final goal of all the purchase process.
     * Can be called after the purchase flow or because the Premium product results already been bought on Google Play.
     */
    private void becomePremium() {
        Global.premium = true;
        Global.settings.save();
        runOnUiThread(this::onBackPressed);
        U.toast(this, R.string.premium_activated);
        p("Premium activated.");
    }

    /**
     * Displays the Google Play dialog to purchase the product.
     */
    void launchFlow() {
        p("Product details:", productDetails);
        if (productDetails == null || productDetails.isEmpty()) return;
        List<BillingFlowParams.ProductDetailsParams> productDetailsParamsList = new ArrayList<>();
        for (ProductDetails details : productDetails) {
            productDetailsParamsList.add(BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details).build());
        }
        BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList).build();
        // Launches the billing flow
        BillingResult billingResult = billingClient.launchBillingFlow(this, billingFlowParams);
        p("Launch billing flow:", billingResult);
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            // User not signed in to Google Account ends here
            // Also if product is already owned ends here
            p("Response OK");
        } else {
            p("Response KO");
        }
    }

    /**
     * Checks on the backend server if the purchase has been already done or not, if not acknowledges it.
     */
    private void verifyPurchase(Purchase purchase) {
        new Thread(() -> {
            try {
                //URL url = new URL("https://www.familygem.app/purchase.php");
                URL url = new URL("http://10.0.2.2:3000/purchase.php"); // Computer localhost = 127.0.0.1
                HttpURLConnection connection = (HttpURLConnection)url.openConnection();
                p("Connection:", connection);
                connection.setRequestMethod("POST");
                connection.setDoOutput(true); // Necessary?
                OutputStream stream = connection.getOutputStream();
                String query = "passKey=" + URLEncoder.encode(BuildConfig.PASS_KEY, "UTF-8") +
                        "&orderId=" + purchase.getOrderId() +
                        "&purchaseTime=" + purchase.getPurchaseTime() +
                        "&purchaseToken=" + purchase.getPurchaseToken();
                stream.write(query.getBytes(StandardCharsets.UTF_8));
                stream.flush();
                stream.close();
                // Answer
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line = reader.readLine();
                reader.close();
                connection.disconnect();
                p("Answer:", line);
                p("Purchase is acknowledged:", purchase.isAcknowledged());
                // Purchase just inserted on backend database
                if (line.equals(String.valueOf(purchase.getPurchaseTime())) && !purchase.isAcknowledged()) {
                    // Acknowledges purchase
                    AcknowledgePurchaseParams acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.getPurchaseToken()).build();
                    billingClient.acknowledgePurchase(acknowledgePurchaseParams, billingResult -> {
                        p("On acknowledge purchase response:", billingResult);
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                            becomePremium();
                        } else {
                            U.toast(this, R.string.something_wrong);
                        }
                    });
                } // Premium already purchased in the past
                else if (line.equals(purchase.getPurchaseToken()) && purchase.isAcknowledged()) {
                    becomePremium();
                } else {
                    U.toast(this, R.string.something_wrong);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        onBackPressed();
        return true;
    }
}

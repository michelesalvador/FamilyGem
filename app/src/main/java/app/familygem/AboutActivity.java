package app.familygem;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryPurchasesParams;

public class AboutActivity extends BaseActivity {

    ProductLayout productLayout;
    View subscribedLayout;
    BillingClient billingClient;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.about_activity);

        TextView version = findViewById(R.id.about_version);
        version.setText(getString(R.string.version_name, BuildConfig.VERSION_NAME));

        TextView webSite = findViewById(R.id.about_link);
        webSite.setOnClickListener(view -> startActivity(
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.familygem.app"))));

        // Premium product layout
        subscribedLayout = findViewById(R.id.about_subscribed);
        productLayout = findViewById(R.id.about_product);
        if (Global.settings.premium) {
            subscribedLayout.setVisibility(View.VISIBLE);
        } else {
            webSite.setVisibility(View.GONE);
            productLayout.initialize(() -> { // Makes it also visible
                runOnUiThread(() -> {
                    productLayout.setVisibility(View.GONE);
                    subscribedLayout.setVisibility(View.VISIBLE);
                });
            });
        }
        subscribedLayout.setOnLongClickListener(view -> {
            PopupMenu popup = new PopupMenu(this, view);
            popup.getMenu().add(0, 0, 0, R.string.delete);
            popup.show();
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 0) connectGoogleBilling();
                return true;
            });
            return true;
        });
    }

    private void connectGoogleBilling() {
        billingClient = BillingClient.newBuilder(this).enablePendingPurchases()
                .setListener((billingResult, purchases) -> {
                }).build();
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    queryHistory();
                } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE) {
                    U.toast(R.string.update_playstore);
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                connectGoogleBilling();
            }
        });
    }

    private void queryHistory() {
        QueryPurchasesParams queryPurchasesParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP).build();
        billingClient.queryPurchasesAsync(queryPurchasesParams, (billingResult, purchases) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK &&
                    !purchases.isEmpty() && purchases.get(0).getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                runOnUiThread(() -> {
                    new AlertDialog.Builder(this).setMessage(R.string.sure_delete)
                            .setPositiveButton(R.string.yes, (dialog, i) -> {
                                consumePurchase(purchases.get(0));
                            }).setNegativeButton(R.string.no, null).show();
                });
            } else U.toast(R.string.something_wrong);
        });
    }

    private void consumePurchase(Purchase purchase) {
        ConsumeParams consumeParams = ConsumeParams.newBuilder().setPurchaseToken(purchase.getPurchaseToken()).build();
        billingClient.consumeAsync(consumeParams, (billingResult, purchaseToken) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                Global.settings.premium = false;
                Global.settings.save();
                // TODO: register consumed purchase on backend server
                runOnUiThread(() -> {
                    subscribedLayout.setVisibility(View.GONE);
                    productLayout.initialize(() -> {
                        runOnUiThread(() -> {
                            productLayout.setVisibility(View.GONE);
                            subscribedLayout.setVisibility(View.VISIBLE);
                        });
                    });
                });
            } else {
                U.toast(R.string.something_wrong);
            }
        });
    }
}

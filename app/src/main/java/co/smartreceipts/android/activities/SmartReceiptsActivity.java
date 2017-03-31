package co.smartreceipts.android.activities;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.util.Set;

import javax.inject.Inject;

import co.smartreceipts.android.R;
import co.smartreceipts.android.ad.AdManager;
import co.smartreceipts.android.analytics.Analytics;
import co.smartreceipts.android.analytics.events.DataPoint;
import co.smartreceipts.android.analytics.events.DefaultDataPointEvent;
import co.smartreceipts.android.analytics.events.Events;
import co.smartreceipts.android.config.ConfigurationManager;
import co.smartreceipts.android.fragments.InformAboutPdfImageAttachmentDialogFragment;
import co.smartreceipts.android.model.Attachment;
import co.smartreceipts.android.persistence.PersistenceManager;
import co.smartreceipts.android.purchases.PurchaseEventsListener;
import co.smartreceipts.android.purchases.PurchaseManager;
import co.smartreceipts.android.purchases.model.InAppPurchase;
import co.smartreceipts.android.purchases.source.PurchaseSource;
import co.smartreceipts.android.purchases.wallet.PurchaseWallet;
import co.smartreceipts.android.settings.UserPreferenceManager;
import co.smartreceipts.android.sync.BackupProvidersManager;
import co.smartreceipts.android.sync.widget.backups.ImportLocalBackupDialogFragment;
import co.smartreceipts.android.utils.FeatureFlags;
import co.smartreceipts.android.utils.log.Logger;
import dagger.android.AndroidInjection;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.subscriptions.CompositeSubscription;
import wb.android.flex.Flex;

public class SmartReceiptsActivity extends WBActivity implements Attachable, PurchaseEventsListener {

    private static final int STORAGE_PERMISSION_REQUEST = 33;
    private static final String READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE";

    @Inject
    AdManager adManager;
    @Inject
    Flex flex;
    @Inject
    PersistenceManager persistenceManager;
    @Inject
    PurchaseWallet purchaseWallet;
    @Inject
    ConfigurationManager configurationManager;
    @Inject
    Analytics analytics;
    @Inject
    PurchaseManager purchaseManager;
    @Inject
    BackupProvidersManager backupProvidersManager;

    private volatile Set<InAppPurchase> availablePurchases;
    private NavigationHandler navigationHandler;
    private Attachment attachment;
    private CompositeSubscription compositeSubscription;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AndroidInjection.inject(this);

        super.onCreate(savedInstanceState);
        Logger.debug(this, "onCreate");

        navigationHandler = new NavigationHandler(this, getSupportFragmentManager(), new FragmentProvider());

        purchaseManager.addEventListener(this);

        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            Logger.debug(this, "savedInstanceState == null");
            navigationHandler.navigateToHomeTripsFragment();
        }

        adManager.onActivityCreated(this, purchaseManager);

        backupProvidersManager.initialize(this);
    }


    @Override
    protected void onStart() {
        super.onStart();
        Logger.debug(this, "onStart");

        if (!persistenceManager.getStorageManager().isExternal()) {
            Toast.makeText(SmartReceiptsActivity.this, flex.getString(this, R.string.SD_WARNING), Toast.LENGTH_LONG).show();
        }

    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        Logger.debug(this, "onResumeFragments");

        // Present dialog for viewing an attachment
        final Attachment attachment = new Attachment(getIntent(), getContentResolver());
        setAttachment(attachment);
        if (attachment.isValid()) {
            final boolean hasStoragePermission = ContextCompat.checkSelfPermission(this, READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            if (attachment.requiresStoragePermissions() && !hasStoragePermission) {
                ActivityCompat.requestPermissions(this, new String[] { READ_EXTERNAL_STORAGE }, STORAGE_PERMISSION_REQUEST);
            } else if (attachment.isDirectlyAttachable()) {
                final UserPreferenceManager preferences = persistenceManager.getPreferenceManager();
                if (InformAboutPdfImageAttachmentDialogFragment.shouldInformAboutPdfImageAttachmentDialogFragment(preferences)) {
                    navigationHandler.showDialog(InformAboutPdfImageAttachmentDialogFragment.newInstance(attachment));
                } else {
                    final int stringId = attachment.isPDF() ? R.string.pdf : R.string.image;
                    Toast.makeText(this, getString(R.string.dialog_attachment_text, getString(stringId)), Toast.LENGTH_LONG).show();
                }
            } else if (attachment.isSMR() && attachment.isActionView()) {
                navigationHandler.showDialog(ImportLocalBackupDialogFragment.newInstance(attachment.getUri()));
            }
        }
        adManager.onResume();

        compositeSubscription = new CompositeSubscription();
        compositeSubscription.add(purchaseManager.getAllAvailablePurchases()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<Set<InAppPurchase>>() {
                    @Override
                    public void call(Set<InAppPurchase> inAppPurchases) {
                        Logger.info(this, "The following purchases are available: {}", availablePurchases);
                        availablePurchases = inAppPurchases;
                        invalidateOptionsMenu(); // To show the subscription option
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        Logger.warn(SmartReceiptsActivity.this, "Failed to retrieve purchases for this session.", throwable);
                    }
                }));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (!purchaseManager.onActivityResult(requestCode, resultCode, data)) {
            if (!backupProvidersManager.onActivityResult(requestCode, resultCode, data)) {
                super.onActivityResult(requestCode, resultCode, data);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        final boolean haveProSubscription = purchaseWallet.hasActivePurchase(InAppPurchase.SmartReceiptsPlus);
        final boolean proSubscriptionIsAvailable = availablePurchases != null && availablePurchases.contains(InAppPurchase.SmartReceiptsPlus);

        // If the pro sub is either unavailable or we already have it, don't show the purchase menu option
        if (!proSubscriptionIsAvailable || haveProSubscription) {
            menu.removeItem(R.id.menu_main_pro_subscription);
        }

        // If we disabled settings in our config, let's remove it
        if (!configurationManager.isSettingsMenuAvailable()) {
            menu.removeItem(R.id.menu_main_settings);
        }

        if (!FeatureFlags.SmartReceiptsLogin.isEnabled()) {
            menu.removeItem(R.id.menu_main_my_account);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_main_settings) {
            navigationHandler.navigateToSettings();
            analytics.record(Events.Navigation.SettingsOverflow);
            return true;
        } else if (item.getItemId() == R.id.menu_main_export) {
            navigationHandler.navigateToBackupMenu();
            analytics.record(Events.Navigation.BackupOverflow);
            return true;
        } else if (item.getItemId() == R.id.menu_main_pro_subscription) {
            purchaseManager.initiatePurchase(InAppPurchase.SmartReceiptsPlus, PurchaseSource.OverflowMenu);
            analytics.record(Events.Navigation.SmartReceiptsPlusOverflow);
            return true;
        } else if (item.getItemId() == R.id.menu_main_my_account) {
            navigationHandler.navigateToLoginScreen();
            analytics.record(Events.Navigation.BackupOverflow);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        if (navigationHandler.shouldFinishOnBackNaviagtion()) {
            finish();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        Logger.info(this, "onPause");
        adManager.onPause();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Logger.debug(this, "pre-onSaveInstanceState");
        super.onSaveInstanceState(outState);
        Logger.debug(this, "post-onSaveInstanceState");
    }

    @Override
    protected void onDestroy() {
        Logger.info(this, "onDestroy");
        adManager.onDestroy();
        purchaseManager.removeEventListener(this);
        persistenceManager.getDatabase().onDestroy();
        super.onDestroy();
    }

    @Override
    public Attachment getAttachment() {
        return attachment;
    }

    @Override
    public void setAttachment(Attachment attachment) {
        this.attachment = attachment;
    }

    @Override
    public void onPurchaseSuccess(@NonNull final InAppPurchase inAppPurchase, @NonNull final PurchaseSource purchaseSource) {
        analytics.record(new DefaultDataPointEvent(Events.Purchases.PurchaseSuccess).addDataPoint(new DataPoint("sku", inAppPurchase.getSku())).addDataPoint(new DataPoint("source", purchaseSource)));
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                invalidateOptionsMenu(); // To hide the subscription option
                Toast.makeText(SmartReceiptsActivity.this, R.string.purchase_succeeded, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onPurchaseFailed(@NonNull final PurchaseSource purchaseSource) {
        analytics.record(new DefaultDataPointEvent(Events.Purchases.PurchaseFailed).addDataPoint(new DataPoint("source", purchaseSource)));
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(SmartReceiptsActivity.this, R.string.purchase_failed, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Nullable
    public PurchaseManager getSubscriptionManager() {
        return purchaseManager;
    }
}

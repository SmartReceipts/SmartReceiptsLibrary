package co.smartreceipts.android;

import android.app.Application;
import android.content.Context;
import android.os.Looper;

import androidx.annotation.VisibleForTesting;
import androidx.multidex.MultiDex;

import com.tom_roush.pdfbox.util.PDFBoxResourceLoader;

import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

import co.smartreceipts.analytics.Analytics;
import co.smartreceipts.analytics.crash.CrashReporter;
import co.smartreceipts.analytics.log.Logger;
import co.smartreceipts.android.ad.MobileAds;
import co.smartreceipts.android.date.DateFormatter;
import co.smartreceipts.android.di.AppComponent;
import co.smartreceipts.android.di.BaseAppModule;
import co.smartreceipts.android.di.DaggerAppComponent;
import co.smartreceipts.android.images.PicassoInitializer;
import co.smartreceipts.android.launch.OnLaunchDataPreFetcher;
import co.smartreceipts.android.ocr.OcrManager;
import co.smartreceipts.android.persistence.DatabaseHelper;
import co.smartreceipts.android.persistence.database.tables.ordering.OrderingPreferencesManager;
import co.smartreceipts.android.purchases.PurchaseManager;
import co.smartreceipts.android.rating.data.AppRatingPreferencesStorage;
import co.smartreceipts.android.receipts.editor.currency.CurrencyInitializer;
import co.smartreceipts.android.receipts.ordering.ReceiptsOrderer;
import co.smartreceipts.android.settings.UserPreferenceManager;
import co.smartreceipts.android.settings.catalog.UserPreference;
import co.smartreceipts.android.subscriptions.SubscriptionsPurchaseTracker;
import co.smartreceipts.android.sync.cleanup.MarkedForDeletionCleaner;
import co.smartreceipts.android.utils.StrictModeConfiguration;
import co.smartreceipts.android.utils.WBUncaughtExceptionHandler;
import co.smartreceipts.android.utils.cache.SmartReceiptsTemporaryFileCache;
import co.smartreceipts.android.utils.rx.DefaultRxErrorHandler;
import co.smartreceipts.android.versioning.AppVersionManager;
import co.smartreceipts.aws.cognito.CognitoManager;
import co.smartreceipts.core.identity.IdentityManager;
import co.smartreceipts.push.PushManager;
import co.smartreceipts.push.PushManagerProvider;
import dagger.android.AndroidInjector;
import dagger.android.DispatchingAndroidInjector;
import dagger.android.HasAndroidInjector;
import io.reactivex.Completable;
import io.reactivex.Scheduler;
import io.reactivex.android.plugins.RxAndroidPlugins;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.Schedulers;
import wb.android.flex.Flex;

public class SmartReceiptsApplication extends Application implements HasAndroidInjector, PushManagerProvider {

    @Inject
    DispatchingAndroidInjector<Object> androidInjector;

    @Inject
    DatabaseHelper databaseHelper;

    @Inject
    Flex flex;

    @Inject
    UserPreferenceManager userPreferenceManager;

    @Inject
    OnLaunchDataPreFetcher onLaunchDataPreFetcher;

    @Inject
    ExtraInitializer extraInitializer;

    @Inject
    CurrencyInitializer currencyInitializer;

    @Inject
    IdentityManager identityManager;

    @Inject
    PurchaseManager purchaseManager;

    @Inject
    PushManager pushManager;

    @Inject
    CognitoManager cognitoManager;

    @Inject
    OcrManager ocrManager;

    @Inject
    CrashReporter crashReporter;

    @Inject
    OrderingPreferencesManager orderingPreferencesManager;

    @Inject
    AppRatingPreferencesStorage appRatingPreferencesStorage;

    @Inject
    Analytics analytics;

    @Inject
    ReceiptsOrderer receiptsOrderer;

    @Inject
    MarkedForDeletionCleaner markedForDeletionCleaner;

    @Inject
    PicassoInitializer picassoInitializer;

    @Inject
    MobileAds mobileAds;

    @Inject
    AppVersionManager appVersionManager;

    @Inject
    DateFormatter dateFormatter;

    @Inject
    SubscriptionsPurchaseTracker subscriptionsPurchaseTracker;

    private AppComponent appComponent;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

         if (BuildConfig.DEBUG) {
             StrictModeConfiguration.enable();
         }

        appComponent = DaggerAppComponent.builder()
                .baseAppModule(new BaseAppModule(this))
                .build();

        appComponent.inject(this);

        WBUncaughtExceptionHandler.initialize();

        Logger.info(this, "\n\n\n\n Launching App...");

        init();
    }

    @NotNull
    @Override
    public PushManager getPushManager() {
        return appComponent.providePushManager();
    }

    @Override
    public AndroidInjector<Object> androidInjector() {
        return androidInjector;
    }

    /**
     * A simple way to allow us to fetch our database helper for database upgrade tests. I attempted
     * to have dagger automatically inject this into our test modules, but I ended up spending far
     * too much time trying to get a TestAppComponent to work properly. For expediency purposes, I'm
     * just using this getter instead
     *
     * @return the {@link DatabaseHelper}
     */
    @VisibleForTesting
    public DatabaseHelper getDatabaseHelper() {
        return databaseHelper;
    }

    /**
     * Similar to our use of {@link #getDatabaseHelper()} above, we supply this getter for our
     * espresso test modules to simplify how we access this content
     *
     * @return the {@link DateFormatter}
     */
    @VisibleForTesting
    public DateFormatter getDateFormatter() {
        return dateFormatter;
    }

    private void init() {

        // To handle RxJava exceptions
        RxJavaPlugins.setErrorHandler(new DefaultRxErrorHandler(analytics));

        // To configure the Android schedulers as per: https://medium.com/@sweers/rxandroids-new-async-api-4ab5b3ad3e93
        final Scheduler asyncMainThreadScheduler = AndroidSchedulers.from(Looper.getMainLooper(), true);
        RxAndroidPlugins.setInitMainThreadSchedulerHandler(scheduler -> asyncMainThreadScheduler);

        flex.initialize();
        userPreferenceManager.initialize();
        purchaseManager.initialize(this);
        orderingPreferencesManager.initialize();
        dateFormatter.initialize();
        onLaunchDataPreFetcher.loadUserData();
        identityManager.initialize();
        pushManager.initialize();
        cognitoManager.initialize();
        ocrManager.initialize();
        crashReporter.initialize(userPreferenceManager.get(UserPreference.Privacy.EnableCrashTracking));
        receiptsOrderer.initialize();
        picassoInitializer.initialize();
        mobileAds.initialize();
        markedForDeletionCleaner.safelyDeleteAllOutstandingItems();
        extraInitializer.init();
        currencyInitializer.init();

        subscriptionsPurchaseTracker.initialize()
                .subscribe(() -> Logger.info(this, "Successfully initialized"),
                        throwable -> Logger.error(this, "Failed to initialize", throwable));

        PDFBoxResourceLoader.init(getApplicationContext());

        // Clear our cache
        Completable.fromAction(() -> new SmartReceiptsTemporaryFileCache(this).resetCache())
                .subscribeOn(Schedulers.io())
                .subscribe();

        // Check if a new version is available
        appVersionManager.onLaunch();

        // Add launch count for rating prompt monitoring
        appRatingPreferencesStorage.incrementLaunchCount();
    }

}

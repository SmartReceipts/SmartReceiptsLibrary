package co.smartreceipts.android;

import android.app.Activity;
import android.app.Application;
import android.support.v4.app.Fragment;

import com.squareup.leakcanary.LeakCanary;
import com.tom_roush.pdfbox.util.PDFBoxResourceLoader;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;

import co.smartreceipts.android.aws.cognito.CognitoManager;
import co.smartreceipts.android.di.AppComponent;
import co.smartreceipts.android.di.BaseAppModule;
import co.smartreceipts.android.di.DaggerAppComponent;
import co.smartreceipts.android.ocr.OcrInteractor;
import co.smartreceipts.android.persistence.DatabaseHelper;
import co.smartreceipts.android.persistence.PersistenceManager;
import co.smartreceipts.android.purchases.PurchaseManager;
import co.smartreceipts.android.push.PushManager;
import co.smartreceipts.android.rating.data.AppRatingPreferencesStorage;
import co.smartreceipts.android.settings.versions.AppVersionManager;
import co.smartreceipts.android.settings.versions.VersionUpgradedListener;
import co.smartreceipts.android.utils.WBUncaughtExceptionHandler;
import co.smartreceipts.android.utils.cache.SmartReceiptsTemporaryFileCache;
import co.smartreceipts.android.utils.log.Logger;
import dagger.android.DispatchingAndroidInjector;
import dagger.android.HasDispatchingActivityInjector;
import dagger.android.support.HasDispatchingSupportFragmentInjector;
import wb.android.storage.SDCardStateException;
import wb.android.storage.StorageManager;

public class SmartReceiptsApplication extends Application implements VersionUpgradedListener,
        HasDispatchingActivityInjector, HasDispatchingSupportFragmentInjector {


    @Inject
    DispatchingAndroidInjector<Activity> activityInjector;
    @Inject
    DispatchingAndroidInjector<Fragment> supportFragmentInjector;

    @Inject
    PersistenceManager persistenceManager;
    @Inject
    ExtraInitializer extraInitializer;
    @Inject
    PurchaseManager purchaseManager;
    @Inject
    PushManager pushManager;
    @Inject
    CognitoManager cognitoManager;
    @Inject
    OcrInteractor ocrInteractor;

    private AppComponent appComponent;

    @Override
    public void onCreate() {
        super.onCreate();

        appComponent = DaggerAppComponent.builder()
                .baseAppModule(new BaseAppModule(this))
                .build();

        appComponent.inject(this);

        configureLog();
        WBUncaughtExceptionHandler.initialize();

        Logger.debug(this, "\n\n\n\n Launching App...");

        init();
    }

    public AppComponent getAppComponent() {
        return appComponent;
    }

    @Override
    public DispatchingAndroidInjector<Activity> activityInjector() {
        return activityInjector;
    }

    @Override
    public DispatchingAndroidInjector<Fragment> supportFragmentInjector() {
        return supportFragmentInjector;
    }

    private void init() {

        pushManager.initialize();
        purchaseManager.initialize(this);
        cognitoManager.initialize();
        ocrInteractor.initialize();

        PDFBoxResourceLoader.init(getApplicationContext());

        // Clear our cache
        new SmartReceiptsTemporaryFileCache(this).resetCache();

        // Check if a new version is available
        new AppVersionManager(this, persistenceManager.getPreferenceManager()).onLaunch(this);

        // Add launch count for rating prompt monitoring
        new AppRatingPreferencesStorage(getApplicationContext()).incrementLaunchCount();

        // LeakCanary initialization
        if (LeakCanary.isInAnalyzerProcess(this)) {
            Logger.debug(this, "Ignoring this process as it's the LeakCanary analyzer one...");
        } else {
            LeakCanary.install(this);
        }

        extraInitializer.init();
    }

    private void configureLog() {
        final String logDirPath = getFilesDir().getPath();
        System.setProperty("LOG_DIR", logDirPath);
    }

    // This is called after _sdCard is available but before _db is
    // This was added after version 78 (version 79 is the first "new" one)
    // Make this a listener
    @Override
    public void onVersionUpgrade(int oldVersion, int newVersion) {
        Logger.debug(this, "Upgrading the app from version {} to {}", oldVersion, newVersion);
        if (oldVersion <= 78) {
            try {
                StorageManager external = persistenceManager.getExternalStorageManager();
                File db = this.getDatabasePath(DatabaseHelper.DATABASE_NAME); // Internal db file
                if (db != null && db.exists()) {
                    File sdDB = external.getFile("receipts.db");
                    if (sdDB.exists()) {
                        sdDB.delete();
                    }
                    Logger.debug(this, "Copying the database file from {} to {}", db.getAbsolutePath(), sdDB.getAbsolutePath());
                    try {
                        external.copy(db, sdDB, true);
                    } catch (IOException e) {
                        Logger.error(this, "Exception occurred when upgrading app version", e);
                    }
                }
            } catch (SDCardStateException e) {
            }
        }
    }
}

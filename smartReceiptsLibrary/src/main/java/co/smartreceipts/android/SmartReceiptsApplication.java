package co.smartreceipts.android;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.multidex.MultiDex;

import com.tom_roush.pdfbox.util.PDFBoxResourceLoader;

import java.io.File;
import java.io.IOException;

import co.smartreceipts.android.analytics.AnalyticsLogger;
import co.smartreceipts.android.analytics.AnalyticsManager;
import co.smartreceipts.android.apis.gson.SmartReceiptsGsonBuilder;
import co.smartreceipts.android.apis.hosts.BetaSmartReceiptsHostConfiguration;
import co.smartreceipts.android.apis.hosts.ServiceManager;
import co.smartreceipts.android.config.ConfigurationManager;
import co.smartreceipts.android.config.DefaultConfigurationManager;
import co.smartreceipts.android.identity.IdentityManager;
import co.smartreceipts.android.model.impl.columns.receipts.ReceiptColumnDefinitions;
import co.smartreceipts.android.persistence.DatabaseHelper;
import co.smartreceipts.android.persistence.PersistenceManager;
import co.smartreceipts.android.persistence.database.controllers.TableControllerManager;
import co.smartreceipts.android.purchases.DefaultSubscriptionCache;
import co.smartreceipts.android.purchases.SubscriptionCache;
import co.smartreceipts.android.rating.data.AppRatingStorageImpl;
import co.smartreceipts.android.settings.versions.AppVersionManager;
import co.smartreceipts.android.settings.versions.VersionUpgradedListener;
import co.smartreceipts.android.sync.BackupProvidersManager;
import co.smartreceipts.android.sync.network.NetworkManager;
import co.smartreceipts.android.utils.cache.SmartReceiptsTemporaryFileCache;
import co.smartreceipts.android.utils.log.Logger;
import co.smartreceipts.android.utils.WBUncaughtExceptionHandler;
import co.smartreceipts.android.workers.WorkerManager;
import wb.android.flex.Flex;
import wb.android.flex.Flexable;
import wb.android.storage.SDCardStateException;
import wb.android.storage.StorageManager;

/**
 * This extends GalleryAppImpl for the camera, since we can only define a single application in the manifest
 *
 * @author WRB
 */
public class SmartReceiptsApplication extends Application implements Flexable, VersionUpgradedListener {

    private WorkerManager mWorkerManager;
    private PersistenceManager mPersistenceManager;
    private Flex mFlex;
    private Activity mCurrentActivity;
    private ConfigurationManager mConfigurationManager;
    private TableControllerManager mTableControllerManager;
    private AnalyticsManager mAnalyticsManager;
    private BackupProvidersManager mBackupProvidersManager;
    private NetworkManager mNetworkManager;
    private IdentityManager mIdentityManager;
    private ServiceManager mServiceManager;
    private boolean mDeferFirstRunDialog;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        configureLog();
        WBUncaughtExceptionHandler.initialize();

        Logger.debug(this, "\n\n\n\n Launching App...");

        mDeferFirstRunDialog = false;
        mFlex = instantiateFlex();
        mConfigurationManager = instantiateConfigurationManager();
        mWorkerManager = instantiateWorkerManager();
        mPersistenceManager = instantiatePersistenceManager();
        mPersistenceManager.initialize(); // TODO: Fix this circular injection pattern

        mAnalyticsManager = new AnalyticsManager(new AnalyticsLogger());
        mTableControllerManager = new TableControllerManager(mPersistenceManager, mAnalyticsManager, new ReceiptColumnDefinitions(this, mPersistenceManager.getDatabase(), mPersistenceManager.getPreferenceManager(), mFlex));
        mNetworkManager = new NetworkManager(this, getPersistenceManager().getPreferenceManager());
        mNetworkManager.initialize();
        mBackupProvidersManager = new BackupProvidersManager(this, getPersistenceManager().getDatabase(), getTableControllerManager(), mNetworkManager, mAnalyticsManager);

        mServiceManager = new ServiceManager(new BetaSmartReceiptsHostConfiguration(), new SmartReceiptsGsonBuilder(new ReceiptColumnDefinitions(this, mPersistenceManager, mFlex)));
        mIdentityManager = new IdentityManager(this, mServiceManager, mAnalyticsManager, mPersistenceManager.getPreferenceManager());

        PDFBoxResourceLoader.init(getApplicationContext());
        
	    // Clear our cache
        new SmartReceiptsTemporaryFileCache(this).resetCache();

        // Check if a new version is available
        new AppVersionManager(this, mPersistenceManager.getPreferenceManager()).onLaunch(this);

        // Add launch count for rating prompt monitoring
        new AppRatingStorageImpl(getApplicationContext()).incrementLaunchCount();
    }

    private void configureLog() {
        final String logDirPath = getFilesDir().getPath() ;
        System.setProperty("LOG_DIR", logDirPath);
    }

    @Override
    public void onTerminate() {
        // Note: This is useful for unit tests but never gets called on actual devices
        mCurrentActivity = null;
        mPersistenceManager.onDestroy();
        mWorkerManager.onDestroy();
        mPersistenceManager = null;
        mWorkerManager = null;
        super.onTerminate();
    }

    public synchronized void setCurrentActivity(Activity activity) {
        mCurrentActivity = activity;
        if (mDeferFirstRunDialog) {
            onFirstRun();
        }
    }

    public synchronized Activity getCurrentActivity() {
        return mCurrentActivity;
    }

    @NonNull
    public WorkerManager getWorkerManager() {
        return mWorkerManager;
    }

    @NonNull
    public PersistenceManager getPersistenceManager() {
        return mPersistenceManager;
    }

    @NonNull
    public IdentityManager getIdentityManager() {
        return mIdentityManager;
    }

    @NonNull
    public ServiceManager getServiceManager() {
        return mServiceManager;
    }

    @NonNull
    public Flex getFlex() {
        return mFlex;
    }

    @NonNull
    public ConfigurationManager getConfigurationManager() {
        return mConfigurationManager;
    }

    @NonNull
    public TableControllerManager getTableControllerManager() {
        return mTableControllerManager;
    }

    @NonNull
    public AnalyticsManager getAnalyticsManager() {
        return mAnalyticsManager;
    }

    @NonNull
    public BackupProvidersManager getBackupProvidersManager() {
        return mBackupProvidersManager;
    }

    @NonNull
    public NetworkManager getNetworkManager() {
        return mNetworkManager;
    }

    @Override
    public int getFleXML() {
        return Flexable.UNDEFINED;
    }

    // This is called after _sdCard is available but before _db is
    // This was added after version 78 (version 79 is the first "new" one)
    // Make this a listener
    @Override
    public void onVersionUpgrade(int oldVersion, int newVersion) {
        Logger.debug(this, "Upgrading the app from version {} to {}", oldVersion, newVersion);
        if (oldVersion <= 78) {
            try {
                StorageManager external = mPersistenceManager.getExternalStorageManager();
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

    // TODO: Update/fix
    public final void onFirstRun() {
        if (mCurrentActivity != null) {
            Logger.debug(this, "Launching first run dialog");
            mDeferFirstRunDialog = false;
            showFirstRunDialog();
        } else {
            if (BuildConfig.DEBUG) {
                Logger.debug(this, "Deferring first run dialog");
            }
            mDeferFirstRunDialog = true;
        }
    }

    protected void showFirstRunDialog() {

    }

    /**
     * Protected method to enable subclasses to create custom instances
     *
     * @return a WorkerManager Instance
     */
    protected WorkerManager instantiateWorkerManager() {
        return new WorkerManager(this);
    }

    /**
     * Protected method to enable subclasses to create custom instances
     *
     * @return a PersistenceManager Instance
     */
    protected PersistenceManager instantiatePersistenceManager() {
        return new PersistenceManager(this, instantiateSubscriptionCache());
    }

    /**
     * Protected method to enable subclasses to create custom instances
     *
     * @return a Flex Instance
     */
    protected Flex instantiateFlex() {
        return Flex.getInstance(this, this);
    }

    /**
     * Protected method to enable subclasses to create custom instances
     *
     * @return a SubscriptionCache Instance
     */
    protected SubscriptionCache instantiateSubscriptionCache() {
        return new DefaultSubscriptionCache(this);
    }

    /**
     * Protected method to enable subclasses to create custom instances
     *
     * @return a ConfigurationManager Instance
     */
    protected ConfigurationManager instantiateConfigurationManager() {
        return new DefaultConfigurationManager(this);
    }

}

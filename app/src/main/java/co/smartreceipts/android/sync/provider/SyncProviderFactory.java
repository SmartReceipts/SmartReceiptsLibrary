package co.smartreceipts.android.sync.provider;

import android.content.Context;
import android.support.annotation.NonNull;

import com.google.common.base.Preconditions;

import co.smartreceipts.android.analytics.Analytics;
import co.smartreceipts.android.persistence.DatabaseHelper;
import co.smartreceipts.android.persistence.database.controllers.TableControllerManager;
import co.smartreceipts.android.sync.BackupProvider;
import co.smartreceipts.android.sync.drive.GoogleDriveBackupManager;
import co.smartreceipts.android.sync.network.NetworkManager;
import co.smartreceipts.android.sync.noop.NoOpBackupProvider;

public class SyncProviderFactory {

    private final Context mContext;
    private final DatabaseHelper mDatabaseHelper;
    private final TableControllerManager mTableControllerManager;
    private final NetworkManager mNetworkManager;
    private final Analytics mAnalytics;

    public SyncProviderFactory(@NonNull Context context, @NonNull DatabaseHelper databaseHelper, @NonNull TableControllerManager tableControllerManager,
                               @NonNull NetworkManager networkManager, @NonNull Analytics analytics) {
        mContext = Preconditions.checkNotNull(context.getApplicationContext());
        mDatabaseHelper = Preconditions.checkNotNull(databaseHelper);
        mTableControllerManager = Preconditions.checkNotNull(tableControllerManager);
        mNetworkManager = Preconditions.checkNotNull(networkManager);
        mAnalytics = Preconditions.checkNotNull(analytics);
    }

    public BackupProvider get(@NonNull SyncProvider syncProvider) {
        if (syncProvider == SyncProvider.GoogleDrive) {
            return new GoogleDriveBackupManager(mContext, mDatabaseHelper, mTableControllerManager, mNetworkManager, mAnalytics);
        } else if (syncProvider == SyncProvider.None) {
            return new NoOpBackupProvider();
        } else {
            throw new IllegalArgumentException("Unsupported sync provider type was specified");
        }
    }
}

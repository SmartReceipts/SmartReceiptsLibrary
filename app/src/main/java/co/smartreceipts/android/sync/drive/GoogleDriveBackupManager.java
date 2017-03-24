package co.smartreceipts.android.sync.drive;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;
import com.google.common.base.Preconditions;

import java.io.File;
import java.lang.ref.WeakReference;
import java.sql.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import co.smartreceipts.android.analytics.Analytics;
import co.smartreceipts.android.model.Category;
import co.smartreceipts.android.model.Column;
import co.smartreceipts.android.model.Distance;
import co.smartreceipts.android.model.PaymentMethod;
import co.smartreceipts.android.model.Receipt;
import co.smartreceipts.android.model.Trip;
import co.smartreceipts.android.persistence.DatabaseHelper;
import co.smartreceipts.android.persistence.database.controllers.TableControllerManager;
import co.smartreceipts.android.sync.BackupProvider;
import co.smartreceipts.android.sync.drive.device.GoogleDriveSyncMetadata;
import co.smartreceipts.android.sync.drive.listeners.DatabaseBackupListener;
import co.smartreceipts.android.sync.drive.listeners.ReceiptBackupListener;
import co.smartreceipts.android.sync.drive.managers.DriveDatabaseManager;
import co.smartreceipts.android.sync.drive.managers.DriveReceiptsManager;
import co.smartreceipts.android.sync.drive.managers.DriveRestoreDataManager;
import co.smartreceipts.android.sync.drive.rx.DriveStreamsManager;
import co.smartreceipts.android.sync.errors.CriticalSyncError;
import co.smartreceipts.android.sync.errors.SyncErrorType;
import co.smartreceipts.android.sync.model.RemoteBackupMetadata;
import co.smartreceipts.android.sync.model.impl.Identifier;
import co.smartreceipts.android.sync.network.NetworkManager;
import co.smartreceipts.android.sync.network.NetworkStateChangeListener;
import co.smartreceipts.android.utils.log.Logger;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.subjects.BehaviorSubject;

public class GoogleDriveBackupManager implements BackupProvider, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, NetworkStateChangeListener {

    /**
     * Request code for auto Google Play Services error resolution.
     */
    private static final int REQUEST_CODE_RESOLUTION = 1;

    private final GoogleApiClient mGoogleApiClient;
    private final DriveStreamsManager mDriveTaskManager;
    private final AtomicReference<WeakReference<FragmentActivity>> mActivityReference;
    private final TableControllerManager mTableControllerManager;
    private final NetworkManager mNetworkManager;
    private final Context mContext;
    private final GoogleDriveSyncMetadata mGoogleDriveSyncMetadata;
    private final DriveReceiptsManager mDriveReceiptsManager;
    private final DriveRestoreDataManager mDriveRestoreDataManager;
    private final DatabaseBackupListener<Trip> mTripDatabaseBackupListener;
    private final ReceiptBackupListener mReceiptDatabaseBackupListener;
    private final DatabaseBackupListener<Distance> mDistanceDatabaseBackupListener;
    private final DatabaseBackupListener<PaymentMethod> mPaymentMethodDatabaseBackupListener;
    private final DatabaseBackupListener<Category> mCategoryDatabaseBackupListener;
    private final DatabaseBackupListener<Column<Receipt>> mCsvColumnDatabaseBackupListener;
    private final DatabaseBackupListener<Column<Receipt>> mPdfColumnDatabaseBackupListener;
    private final BehaviorSubject<Throwable> mSyncErrorStream;

    public GoogleDriveBackupManager(@NonNull Context context, @NonNull DatabaseHelper databaseHelper, @NonNull TableControllerManager tableControllerManager,
                                    @NonNull NetworkManager networkManager, @NonNull Analytics analytics) {
        mGoogleApiClient = new GoogleApiClient.Builder(context.getApplicationContext())
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_APPFOLDER)
                .useDefaultAccount()
                .build();
        mContext = Preconditions.checkNotNull(context.getApplicationContext());
        mGoogleDriveSyncMetadata = new GoogleDriveSyncMetadata(context);
        mTableControllerManager = Preconditions.checkNotNull(tableControllerManager);
        mNetworkManager = Preconditions.checkNotNull(networkManager);
        mSyncErrorStream = BehaviorSubject.create();
        mDriveTaskManager = new DriveStreamsManager(context, mGoogleApiClient, mGoogleDriveSyncMetadata, mSyncErrorStream);
        mActivityReference = new AtomicReference<>(new WeakReference<FragmentActivity>(null));

        final DriveDatabaseManager driveDatabaseManager = new DriveDatabaseManager(context, mDriveTaskManager, mGoogleDriveSyncMetadata, mNetworkManager, analytics);
        mDriveReceiptsManager = new DriveReceiptsManager(tableControllerManager.getReceiptTableController(), databaseHelper.getReceiptsTable(), mDriveTaskManager, driveDatabaseManager, mNetworkManager, analytics);
        mDriveRestoreDataManager = new DriveRestoreDataManager(context, mDriveTaskManager, databaseHelper, driveDatabaseManager);

        mTripDatabaseBackupListener = new DatabaseBackupListener<>(driveDatabaseManager);
        mReceiptDatabaseBackupListener = new ReceiptBackupListener(driveDatabaseManager, mDriveReceiptsManager);
        mDistanceDatabaseBackupListener = new DatabaseBackupListener<>(driveDatabaseManager);
        mPaymentMethodDatabaseBackupListener = new DatabaseBackupListener<>(driveDatabaseManager);
        mCategoryDatabaseBackupListener = new DatabaseBackupListener<>(driveDatabaseManager);
        mCsvColumnDatabaseBackupListener = new DatabaseBackupListener<>(driveDatabaseManager);
        mPdfColumnDatabaseBackupListener = new DatabaseBackupListener<>(driveDatabaseManager);
    }

    @Override
    public void initialize(@Nullable FragmentActivity activity) {
        Preconditions.checkNotNull(activity, "Google Drive requires a valid activity to be provided");

        final FragmentActivity existingActivity = mActivityReference.get().get();
        if (!activity.equals(existingActivity)) {
            mActivityReference.set(new WeakReference<>(activity));
        }
        if (!isConnectedOrConnecting()) {
            mGoogleApiClient.connect();
        }
        mNetworkManager.registerListener(this);
    }

    @Override
    public void deinitialize() {
        mNetworkManager.unregisterListener(this);
        if (isConnectedOrConnecting()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_CODE_RESOLUTION && resultCode == Activity.RESULT_OK) {
            if (!isConnectedOrConnecting()) {
                mGoogleApiClient.connect();
            }
            return true;
        } else {
            return false;
        }
    }

    @NonNull
    @Override
    public Observable<List<RemoteBackupMetadata>> getRemoteBackups() {
        return mDriveTaskManager.getRemoteBackups();
    }

    @Nullable
    @Override
    public Identifier getDeviceSyncId() {
        return mGoogleDriveSyncMetadata.getDeviceIdentifier();
    }

    @NonNull
    @Override
    public Date getLastDatabaseSyncTime() {
        return mGoogleDriveSyncMetadata.getLastDatabaseSyncTime();
    }

    @NonNull
    @Override
    public Observable<Boolean> restoreBackup(@NonNull RemoteBackupMetadata remoteBackupMetadata, boolean overwriteExistingData) {
        return mDriveRestoreDataManager.restoreBackup(remoteBackupMetadata, overwriteExistingData);
    }

    @NonNull
    @Override
    public Observable<Boolean> deleteBackup(@NonNull RemoteBackupMetadata remoteBackupMetadata) {
        Preconditions.checkNotNull(remoteBackupMetadata);

        if (remoteBackupMetadata.getSyncDeviceId().equals(mGoogleDriveSyncMetadata.getDeviceIdentifier())) {
            mGoogleDriveSyncMetadata.clear();
            mDriveReceiptsManager.disable();
        }
        return mDriveTaskManager.delete(remoteBackupMetadata.getId())
                .doOnNext(new Action1<Boolean>() {
                    @Override
                    public void call(Boolean success) {
                        mDriveReceiptsManager.enable();
                        if (success) {
                            mDriveReceiptsManager.initialize();
                        }
                    }
                });
    }

    @Override
    public Observable<Boolean> clearCurrentBackupConfiguration() {
        mDriveReceiptsManager.disable();
        mGoogleDriveSyncMetadata.clear();
        mDriveTaskManager.clearCachedData();
        // Note: We added a stupid delay hack here to allow things to clear out of their buffers
        return Observable.just(true)
                .delay(500, TimeUnit.MILLISECONDS)
                .doOnNext(new Action1<Boolean>() {
                    @Override
                    public void call(Boolean success) {
                        mDriveReceiptsManager.enable();
                        if (success) {
                            mDriveReceiptsManager.initialize();
                        }
                    }
                });
    }

    @NonNull
    @Override
    public Observable<List<File>> downloadAllData(@NonNull RemoteBackupMetadata remoteBackupMetadata, @NonNull File downloadLocation) {
        return mDriveRestoreDataManager.downloadAllBackupMetadataImages(remoteBackupMetadata, downloadLocation);
    }

    @NonNull
    @Override
    public Observable<List<File>> debugDownloadAllData(@NonNull RemoteBackupMetadata remoteBackupMetadata, @NonNull File downloadLocation) {
        return mDriveRestoreDataManager.downloadAllFilesInDriveFolder(remoteBackupMetadata, downloadLocation);
    }

    @NonNull
    @Override
    public Observable<CriticalSyncError> getCriticalSyncErrorStream() {
        return mSyncErrorStream.asObservable()
                .map(new Func1<Throwable, CriticalSyncError>() {
                    @Override
                    public CriticalSyncError call(Throwable throwable) {
                        if (throwable instanceof CriticalSyncError) {
                            return (CriticalSyncError) throwable;
                        } else {
                            return null;
                        }
                    }
                })
                .filter(new Func1<CriticalSyncError, Boolean>() {
                    @Override
                    public Boolean call(CriticalSyncError criticalSyncError) {
                        return criticalSyncError != null;
                    }
                });
    }

    @Override
    public void markErrorResolved(@NonNull SyncErrorType syncErrorType) {
        mSyncErrorStream.onNext(null);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult result) {
        Logger.warn(this, "GoogleApiClient connection failed: {}", result);

        final FragmentActivity activity = mActivityReference.get().get();
        if (activity == null) {
            Logger.error(this, "The parent activity was destroyed. Unable to resolve GoogleApiClient connection failure.");
            return;
        }

        try {
            if (!result.hasResolution()) {
                GoogleApiAvailability.getInstance().getErrorDialog(activity, result.getErrorCode(), 0).show();
                return;
            }
            try {
                result.startResolutionForResult(activity, REQUEST_CODE_RESOLUTION);
            } catch (IntentSender.SendIntentException e) {
                Logger.error(this, "Exception while starting resolution activity", e);
            }
        } catch (IllegalStateException e) {
            Logger.warn(this,  "The parent activity is in a bad state.. Unable to resolve GoogleApiClient connection failure.");
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        mDriveTaskManager.onConnected(bundle);
        mDriveReceiptsManager.initialize();
        mTableControllerManager.getTripTableController().subscribe(mTripDatabaseBackupListener);
        mTableControllerManager.getReceiptTableController().subscribe(mReceiptDatabaseBackupListener);
        mTableControllerManager.getDistanceTableController().subscribe(mDistanceDatabaseBackupListener);
        mTableControllerManager.getPaymentMethodsTableController().subscribe(mPaymentMethodDatabaseBackupListener);
        mTableControllerManager.getCategoriesTableController().subscribe(mCategoryDatabaseBackupListener);
        mTableControllerManager.getCSVTableController().subscribe(mCsvColumnDatabaseBackupListener);
        mTableControllerManager.getPDFTableController().subscribe(mPdfColumnDatabaseBackupListener);
    }

    @Override
    public void onConnectionSuspended(int cause) {
        mTableControllerManager.getTripTableController().unsubscribe(mTripDatabaseBackupListener);
        mTableControllerManager.getReceiptTableController().unsubscribe(mReceiptDatabaseBackupListener);
        mTableControllerManager.getDistanceTableController().unsubscribe(mDistanceDatabaseBackupListener);
        mTableControllerManager.getPaymentMethodsTableController().unsubscribe(mPaymentMethodDatabaseBackupListener);
        mTableControllerManager.getCategoriesTableController().unsubscribe(mCategoryDatabaseBackupListener);
        mTableControllerManager.getCSVTableController().unsubscribe(mCsvColumnDatabaseBackupListener);
        mTableControllerManager.getPDFTableController().unsubscribe(mPdfColumnDatabaseBackupListener);
        mDriveTaskManager.onConnectionSuspended(cause);
    }

    @Override
    public void onNetworkConnectivityLost() {

    }

    @Override
    public void onNetworkConnectivityGained() {
        Logger.info(this, "Handling a NetworkConnectivityGained event for drive");
        if (!isConnectedOrConnecting()) {
            final FragmentActivity existingActivity = mActivityReference.get().get();
            if (existingActivity != null) {
                initialize(existingActivity);
            }
        } else {
            mDriveReceiptsManager.initialize();
        }
    }

    private boolean isConnectedOrConnecting() {
        return mGoogleApiClient.isConnected() || mGoogleApiClient.isConnecting();
    }
}

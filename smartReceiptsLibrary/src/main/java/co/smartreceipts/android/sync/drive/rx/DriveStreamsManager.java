package co.smartreceipts.android.sync.drive.rx;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.Metadata;
import com.google.common.base.Preconditions;

import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import co.smartreceipts.android.sync.drive.device.GoogleDriveSyncMetadata;
import co.smartreceipts.android.sync.drive.error.DriveThrowableToSyncErrorTranslator;
import co.smartreceipts.android.sync.model.RemoteBackupMetadata;
import co.smartreceipts.android.sync.model.SyncState;
import co.smartreceipts.android.sync.model.impl.Identifier;
import co.smartreceipts.android.sync.provider.SyncProvider;
import co.smartreceipts.android.utils.log.Logger;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.subjects.Subject;

public class DriveStreamsManager implements GoogleApiClient.ConnectionCallbacks {

    private final DriveDataStreams mDriveDataStreams;
    private final DriveStreamMappings mDriveStreamMappings;
    private final Subject<Throwable, Throwable> mDriveErrorStream;
    private final DriveThrowableToSyncErrorTranslator mSyncErrorTranslator;
    private final AtomicReference<CountDownLatch> mLatchReference;

    public DriveStreamsManager(@NonNull Context context, @NonNull GoogleApiClient googleApiClient, @NonNull GoogleDriveSyncMetadata googleDriveSyncMetadata,
                               @NonNull Subject<Throwable, Throwable> driveErrorStream) {
        this(new DriveDataStreams(context, googleApiClient, googleDriveSyncMetadata), new DriveStreamMappings(), driveErrorStream, new DriveThrowableToSyncErrorTranslator());
    }

    public DriveStreamsManager(@NonNull DriveDataStreams driveDataStreams, @NonNull DriveStreamMappings driveStreamMappings,
                               @NonNull Subject<Throwable, Throwable> driveErrorStream, @NonNull DriveThrowableToSyncErrorTranslator syncErrorTranslator) {
        mDriveDataStreams = Preconditions.checkNotNull(driveDataStreams);
        mDriveStreamMappings = Preconditions.checkNotNull(driveStreamMappings);
        mDriveErrorStream = Preconditions.checkNotNull(driveErrorStream);
        mSyncErrorTranslator = Preconditions.checkNotNull(syncErrorTranslator);
        mLatchReference = new AtomicReference<>(new CountDownLatch(1));
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Logger.info(this, "GoogleApiClient connection succeeded.");
        mLatchReference.get().countDown();
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Logger.info(this, "GoogleApiClient connection suspended with cause {}", cause);
        mLatchReference.set(new CountDownLatch(1));
    }

    @NonNull
    public Observable<List<RemoteBackupMetadata>> getRemoteBackups() {
        return newBlockUntilConnectedObservable()
                .flatMap(new Func1<Void, Observable<List<RemoteBackupMetadata>>>() {
                    @Override
                    public Observable<List<RemoteBackupMetadata>> call(Void aVoid) {
                        return mDriveDataStreams.getSmartReceiptsFolders();
                    }
                })
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        mDriveErrorStream.onNext(mSyncErrorTranslator.get(throwable));
                    }
                });
    }

    @NonNull
    public synchronized Observable<DriveId> getDriveId(@NonNull final Identifier identifier) {
        Preconditions.checkNotNull(identifier);

        return newBlockUntilConnectedObservable()
                .flatMap(new Func1<Void, Observable<DriveId>>() {
                    @Override
                    public Observable<DriveId> call(Void aVoid) {
                        return mDriveDataStreams.getDriveId(identifier);
                    }
                })
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        mDriveErrorStream.onNext(mSyncErrorTranslator.get(throwable));
                    }
                });
    }

    @NonNull
    public synchronized Observable<DriveId> getFilesInFolder(@NonNull final DriveFolder driveFolder) {
        Preconditions.checkNotNull(driveFolder);

        return newBlockUntilConnectedObservable()
                .flatMap(new Func1<Void, Observable<DriveId>>() {
                    @Override
                    public Observable<DriveId> call(Void aVoid) {
                        return mDriveDataStreams.getFilesInFolder(driveFolder);
                    }
                })
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        mDriveErrorStream.onNext(mSyncErrorTranslator.get(throwable));
                    }
                });

    }

    @NonNull
    public synchronized Observable<DriveId> getFilesInFolder(@NonNull final DriveFolder driveFolder, @NonNull final String fileName) {
        Preconditions.checkNotNull(driveFolder);
        Preconditions.checkNotNull(fileName);

        return newBlockUntilConnectedObservable()
                .flatMap(new Func1<Void, Observable<DriveId>>() {
                    @Override
                    public Observable<DriveId> call(Void aVoid) {
                        return mDriveDataStreams.getFilesInFolder(driveFolder, fileName);
                    }
                })
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        mDriveErrorStream.onNext(mSyncErrorTranslator.get(throwable));
                    }
                });

    }

    @NonNull
    public synchronized Observable<Metadata> getMetadata(@NonNull final DriveFile driveFile) {
        Preconditions.checkNotNull(driveFile);

        return newBlockUntilConnectedObservable()
                .flatMap(new Func1<Void, Observable<Metadata>>() {
                    @Override
                    public Observable<Metadata> call(Void aVoid) {
                        return mDriveDataStreams.getMetadata(driveFile);
                    }
                })
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        mDriveErrorStream.onNext(mSyncErrorTranslator.get(throwable));
                    }
                });

    }

    @NonNull
    public Observable<SyncState> uploadFileToDrive(@NonNull final SyncState currentSyncState, @NonNull final File file) {
        Preconditions.checkNotNull(currentSyncState);
        Preconditions.checkNotNull(file);

        return newBlockUntilConnectedObservable()
                .flatMap(new Func1<Void, Observable<DriveFolder>>() {
                    @Override
                    public Observable<DriveFolder> call(Void aVoid) {
                        return mDriveDataStreams.getSmartReceiptsFolder();
                    }
                })
                .flatMap(new Func1<DriveFolder, Observable<DriveFile>>() {
                    @Override
                    public Observable<DriveFile> call(DriveFolder driveFolder) {
                        return mDriveDataStreams.createFileInFolder(driveFolder, file);
                    }
                })
                .flatMap(new Func1<DriveFile, Observable<SyncState>>() {
                    @Override
                    public Observable<SyncState> call(DriveFile driveFile) {
                        return Observable.just(mDriveStreamMappings.postInsertSyncState(currentSyncState, driveFile));
                    }
                })
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        mDriveErrorStream.onNext(mSyncErrorTranslator.get(throwable));
                    }
                });
    }

    @NonNull
    public Observable<Identifier> uploadFileToDrive(@NonNull final File file) {
        Preconditions.checkNotNull(file);

        return newBlockUntilConnectedObservable()
                .flatMap(new Func1<Void, Observable<DriveFolder>>() {
                    @Override
                    public Observable<DriveFolder> call(Void aVoid) {
                        return mDriveDataStreams.getSmartReceiptsFolder();
                    }
                })
                .flatMap(new Func1<DriveFolder, Observable<DriveFile>>() {
                    @Override
                    public Observable<DriveFile> call(DriveFolder driveFolder) {
                        return mDriveDataStreams.createFileInFolder(driveFolder, file);
                    }
                })
                .flatMap(new Func1<DriveFile, Observable<Identifier>>() {
                    @Override
                    public Observable<Identifier> call(DriveFile driveFile) {
                        return Observable.just(new Identifier(driveFile.getDriveId().getResourceId()));
                    }
                })
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        mDriveErrorStream.onNext(mSyncErrorTranslator.get(throwable));
                    }
                });
    }

    @NonNull
    public Observable<SyncState> updateDriveFile(@NonNull final SyncState currentSyncState, @NonNull final File file) {
        Preconditions.checkNotNull(currentSyncState);
        Preconditions.checkNotNull(file);

        return newBlockUntilConnectedObservable()
                .flatMap(new Func1<Void, Observable<DriveFile>>() {
                    @Override
                    public Observable<DriveFile> call(Void aVoid) {
                        final Identifier driveIdentifier = currentSyncState.getSyncId(SyncProvider.GoogleDrive);
                        if (driveIdentifier != null) {
                            return mDriveDataStreams.updateFile(driveIdentifier, file);
                        } else {
                            return Observable.error(new Exception("This sync state doesn't include a valid Drive Identifier"));
                        }
                    }
                })
                .flatMap(new Func1<DriveFile, Observable<SyncState>>() {
                    @Override
                    public Observable<SyncState> call(DriveFile driveFile) {
                        return Observable.just(mDriveStreamMappings.postUpdateSyncState(currentSyncState, driveFile));
                    }
                })
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        mDriveErrorStream.onNext(mSyncErrorTranslator.get(throwable));
                    }
                });
    }

    @NonNull
    public Observable<Identifier> updateDriveFile(@NonNull final Identifier currentIdentifier, @NonNull final File file) {
        Preconditions.checkNotNull(currentIdentifier);
        Preconditions.checkNotNull(file);

        return newBlockUntilConnectedObservable()
                .flatMap(new Func1<Void, Observable<DriveFile>>() {
                    @Override
                    public Observable<DriveFile> call(Void aVoid) {
                        return mDriveDataStreams.updateFile(currentIdentifier, file);
                    }
                })
                .flatMap(new Func1<DriveFile, Observable<Identifier>>() {
                    @Override
                    public Observable<Identifier> call(DriveFile driveFile) {
                        return Observable.just(new Identifier(driveFile.getDriveId().getResourceId()));
                    }
                })
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        mDriveErrorStream.onNext(mSyncErrorTranslator.get(throwable));
                    }
                });
    }

    @NonNull
    public Observable<SyncState> deleteDriveFile(@NonNull final SyncState currentSyncState, final boolean isFullDelete) {
        Preconditions.checkNotNull(currentSyncState);

        return newBlockUntilConnectedObservable()
                .flatMap(new Func1<Void, Observable<Boolean>>() {
                    @Override
                    public Observable<Boolean> call(Void aVoid) {
                        final Identifier driveIdentifier = currentSyncState.getSyncId(SyncProvider.GoogleDrive);
                        if (driveIdentifier != null) {
                            return mDriveDataStreams.delete(driveIdentifier);
                        } else {
                            return Observable.just(true);
                        }
                    }
                })
                .flatMap(new Func1<Boolean, Observable<SyncState>>() {
                    @Override
                    public Observable<SyncState> call(Boolean success) {
                        if (success) {
                            return Observable.just(mDriveStreamMappings.postDeleteSyncState(currentSyncState, isFullDelete));
                        } else {
                            return Observable.just(currentSyncState);
                        }
                    }
                })
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        mDriveErrorStream.onNext(mSyncErrorTranslator.get(throwable));
                    }
                });
    }

    @NonNull
    public Observable<Boolean> delete(@NonNull final Identifier identifier) {
        Preconditions.checkNotNull(identifier);

        return newBlockUntilConnectedObservable()
                .flatMap(new Func1<Void, Observable<Boolean>>() {
                    @Override
                    public Observable<Boolean> call(Void aVoid) {
                        return mDriveDataStreams.delete(identifier);
                    }
                })
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        mDriveErrorStream.onNext(mSyncErrorTranslator.get(throwable));
                    }
                });
    }

    public void clearCachedData() {
        mDriveDataStreams.clear();
    }

    @NonNull
    public Observable<File> download(@NonNull final DriveFile driveFile, @NonNull final File downloadLocationFile) {
        Preconditions.checkNotNull(driveFile);
        Preconditions.checkNotNull(downloadLocationFile);

        return newBlockUntilConnectedObservable()
                .flatMap(new Func1<Void, Observable<File>>() {
                    @Override
                    public Observable<File> call(Void aVoid) {
                        return mDriveDataStreams.download(driveFile, downloadLocationFile);
                    }
                })
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        mDriveErrorStream.onNext(mSyncErrorTranslator.get(throwable));
                    }
                });
    }

    @NonNull
    private Observable<Void> newBlockUntilConnectedObservable() {
        return Observable.create(new Observable.OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                final CountDownLatch countDownLatch = mLatchReference.get();
                try {
                    countDownLatch.await();
                    subscriber.onNext(null);
                    subscriber.onCompleted();
                } catch (InterruptedException e) {
                    subscriber.onError(e);
                }
            }
        });
    }

}

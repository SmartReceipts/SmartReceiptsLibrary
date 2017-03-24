package co.smartreceipts.android.sync.manual;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;

import com.google.common.base.Preconditions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import co.smartreceipts.android.persistence.DatabaseHelper;
import co.smartreceipts.android.persistence.PersistenceManager;
import co.smartreceipts.android.utils.log.Logger;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.schedulers.Schedulers;
import rx.subjects.ReplaySubject;
import wb.android.storage.SDCardFileManager;
import wb.android.storage.SDCardStateException;
import wb.android.storage.StorageManager;

public class ManualRestoreTask {

    private final PersistenceManager mPersistenceManager;
    private final Context mContext;
    private final Scheduler mObserveOnScheduler;
    private final Scheduler mSubscribeOnScheduler;
    private final Map<RestoreRequest, ReplaySubject<Boolean>> mRestoreSubjectMap = new HashMap<>();

    ManualRestoreTask(@NonNull PersistenceManager persistenceManager, @NonNull Context context) {
        this(persistenceManager, context, Schedulers.io(), Schedulers.io());
    }

    ManualRestoreTask(@NonNull PersistenceManager persistenceManager, @NonNull Context context, @NonNull Scheduler observeOnScheduler, @NonNull Scheduler subscribeOnScheduler) {
        mPersistenceManager = Preconditions.checkNotNull(persistenceManager);
        mContext = Preconditions.checkNotNull(context.getApplicationContext());
        mObserveOnScheduler = Preconditions.checkNotNull(observeOnScheduler);
        mSubscribeOnScheduler = Preconditions.checkNotNull(subscribeOnScheduler);
    }

    @NonNull
    public synchronized ReplaySubject<Boolean> restoreData(@NonNull final Uri uri, final boolean overwrite) {
        final RestoreRequest restoreRequest = new RestoreRequest(uri, overwrite);
        ReplaySubject<Boolean> restoreReplaySubject = mRestoreSubjectMap.get(restoreRequest);
        if (restoreReplaySubject == null) {
            restoreReplaySubject = ReplaySubject.create();
            restoreDataToObservable(uri, overwrite)
                    .observeOn(mObserveOnScheduler)
                    .subscribeOn(mSubscribeOnScheduler)
                    .subscribe(restoreReplaySubject);
            mRestoreSubjectMap.put(restoreRequest, restoreReplaySubject);
        }
        return restoreReplaySubject;
    }

    public Observable<Boolean> restoreDataToObservable(@NonNull final Uri uri, final boolean overwrite) {
        return Observable.create(new Observable.OnSubscribe<Boolean>() {
            @Override
            public void call(Subscriber<? super Boolean> subscriber) {
                Logger.debug(this, "Starting log task at {}", System.currentTimeMillis());
                Logger.debug(this, "Uri: {}", uri);
                try {
                    SDCardFileManager external = mPersistenceManager.getExternalStorageManager();
                    if (external.getFile(ManualBackupTask.DATABASE_EXPORT_NAME).delete()); {
                        Logger.debug(this, "Deleting existing backup database");
                    }
                    String scheme = uri.getScheme();
                    if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {
                        Logger.debug(this, "Processing URI with accepted scheme.");
                        InputStream is = null;
                        try {
                            ContentResolver cr = mContext.getContentResolver();
                            is = cr.openInputStream(uri);
                            File dest = external.getFile("smart.zip");
                            external.delete(dest);
                            if (!external.copy(is, dest, true)) {
                                Logger.debug(this, "Copy failed.");
                                subscriber.onNext(false);
                                subscriber.onCompleted();
                            } else {
                                final boolean importResult = importAll(external, dest, overwrite);
                                external.delete(dest);
                                subscriber.onNext(importResult);
                                subscriber.onCompleted();
                            }
                        }
                        catch (IOException e) {
                            Logger.error(ManualRestoreTask.this, "Caught exception during import at [1]", e);
                            subscriber.onError(e);
                        }
                        finally {
                            StorageManager.closeQuietly(is);
                        }
                    }
                    else {
                        Logger.debug(this, "Processing URI with unknown scheme.");
                        File src = null;
                        File dest = external.getFile("smart.zip");
                        external.delete(dest);
                        if (uri.getPath() != null) {
                            src = new File(uri.getPath());
                        }
                        else if (uri.getEncodedPath() != null) {
                            src = new File(uri.getEncodedPath());
                        }
                        if (src == null || !src.exists()) {
                            Logger.debug(ManualRestoreTask.this, "Unknown source.");
                            subscriber.onNext(false);
                            subscriber.onCompleted();
                        }
                        if (!external.copy(src, dest, true)) {
                            Logger.debug(ManualRestoreTask.this, "Copy failed.");
                            subscriber.onNext(false);
                            subscriber.onCompleted();
                        } else {
                            final boolean importResult = importAll(external, dest, overwrite);
                            external.delete(dest);
                            subscriber.onNext(importResult);
                            subscriber.onCompleted();
                        }
                    }
                } catch (IOException | SDCardStateException e) {
                    Logger.error(ManualRestoreTask.this, "Caught exception during import at [2]", e);
                    subscriber.onError(e);
                }
            }
        });
    }

    private boolean importAll(SDCardFileManager external, File file, final boolean overwrite) {
        Logger.debug(this, "import all : {} {}", file, overwrite);
        if (!external.unzip(file, overwrite)) {
            Logger.debug(this, "Unzip failed");
            return false;
        }
        StorageManager internal = mPersistenceManager.getInternalStorageManager();
        File sdPrefs = external.getFile("shared_prefs");
        File prefs = internal.getFile(internal.getRoot().getParentFile(), "shared_prefs");
        try {
            if (!internal.copy(sdPrefs, prefs, overwrite)) {
                Logger.error(this, "Failed to import settings");
            }
        }
        catch (IOException e) {
            Logger.error(this, "Caught IOexception during import at [3]", e.toString());
        }
        try {
            File internalDir = external.getFile("Internal");
            internal.copy(internalDir, internal.getRoot(), overwrite);
        }
        catch (IOException e) {
            Logger.error(this, "Caught IOexception during import at [4]", e.toString());
        }
        DatabaseHelper db = mPersistenceManager.getDatabase();
        Logger.debug(this, "Merging database");
        return db.merge(external.getFile(ManualBackupTask.DATABASE_EXPORT_NAME).getAbsolutePath(), mContext.getPackageName(), overwrite);
    }

    private static final class RestoreRequest {

        private final Uri mUri;
        private final boolean mOverwrite;

        public RestoreRequest(@NonNull Uri uri, boolean overwrite) {
            mUri = Preconditions.checkNotNull(uri);
            mOverwrite = overwrite;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RestoreRequest)) return false;

            RestoreRequest that = (RestoreRequest) o;

            if (mOverwrite != that.mOverwrite) return false;
            return mUri.equals(that.mUri);

        }

        @Override
        public int hashCode() {
            int result = mUri.hashCode();
            result = 31 * result + (mOverwrite ? 1 : 0);
            return result;
        }
    }
}

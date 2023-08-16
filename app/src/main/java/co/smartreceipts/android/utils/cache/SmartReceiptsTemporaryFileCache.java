package co.smartreceipts.android.utils.cache;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.common.base.Preconditions;

import java.io.File;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import co.smartreceipts.analytics.log.Logger;
import co.smartreceipts.core.di.scopes.ApplicationScope;
import wb.android.storage.StorageManager;

/**
 * A bunch of classes.dex files also get saved in {@link Context#getCacheDir()}, so we uses this class
 * to create a special smart receipts subfolder that we can safely wipe upon each app launch
 */
@ApplicationScope
public class SmartReceiptsTemporaryFileCache {

    private static final String FOLDER_NAME = "smartReceiptsTmp";

    private final Context context;
    private final StorageManager storageManager;
    private final File internalTemporaryCacheFolder;

    @Inject
    public SmartReceiptsTemporaryFileCache(@NonNull Context context) {
        this.context = Preconditions.checkNotNull(context.getApplicationContext());
        this.storageManager = StorageManager.getInstance(context);
        this.internalTemporaryCacheFolder = new File(Preconditions.checkNotNull(context.getCacheDir()), FOLDER_NAME);
    }

    /**
     * Returns a file in the <b>internal</b> cache folder
     *
     * @param filename the name of the file
     * @return the a {@link File}
     */
    @NonNull
    public File getInternalCacheFile(@NonNull String filename) {
        return new File(internalTemporaryCacheFolder, filename);
    }

    public void resetCache() {
        Logger.info(SmartReceiptsTemporaryFileCache.this, "Clearing the cached dir");
        Executors.newSingleThreadExecutor().execute(() -> {
            for (final File cacheDir : Collections.singletonList(internalTemporaryCacheFolder)) {
                //noinspection ResultOfMethodCallIgnored
                cacheDir.mkdirs();
                final File[] files = cacheDir.listFiles();
                if (files != null) {
                    for (final File file : files) {
                        // Note: Only delete this file is it was modified more than a day ago to buy some cache buffer time
                        if (System.currentTimeMillis() > file.lastModified() + TimeUnit.DAYS.toMillis(1)) {
                            Logger.debug(SmartReceiptsTemporaryFileCache.this, "Recursively deleting cached file: {}", file);
                            storageManager.deleteRecursively(file);
                        }
                    }
                }
            }
        });
    }
}

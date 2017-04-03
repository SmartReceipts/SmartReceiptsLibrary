package co.smartreceipts.android.sync.provider;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;

import com.google.common.base.Preconditions;

import javax.inject.Inject;

public class SyncProviderStore {

    private static final String KEY_SYNC_PROVIDER = "key_sync_provider_1";

    private final SharedPreferences mSharedPreferences;

    @Inject
    public SyncProviderStore(Context context) {
        this(PreferenceManager.getDefaultSharedPreferences(context));
    }

    private SyncProviderStore(@NonNull SharedPreferences sharedPreferences) {
        mSharedPreferences = Preconditions.checkNotNull(sharedPreferences);
    }

    @NonNull
    public synchronized SyncProvider getProvider() {
        final String syncProviderName = mSharedPreferences.getString(KEY_SYNC_PROVIDER, "");
        try {
            return SyncProvider.valueOf(syncProviderName);
        } catch (IllegalArgumentException e) {
            return SyncProvider.None;
        }
    }

    public boolean setSyncProvider(@NonNull SyncProvider syncProvider) {
        final SyncProvider currentValue = getProvider();
        if (currentValue != syncProvider) {
            mSharedPreferences.edit().putString(KEY_SYNC_PROVIDER, syncProvider.name()).apply();
            return true;
        } else {
            return false;
        }
    }

}

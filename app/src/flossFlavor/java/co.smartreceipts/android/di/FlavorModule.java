package co.smartreceipts.android.di;

import java.util.Arrays;

import co.smartreceipts.android.ExtraInitializer;
import co.smartreceipts.android.ExtraInitializerFlossImpl;
import co.smartreceipts.android.analytics.Analytics;
import co.smartreceipts.android.analytics.AnalyticsManager;
import co.smartreceipts.android.analytics.impl.logger.AnalyticsLogger;
import co.smartreceipts.android.di.scopes.ApplicationScope;
import co.smartreceipts.android.purchases.wallet.DefaultPurchaseWallet;
import co.smartreceipts.android.purchases.wallet.PurchaseWallet;
import co.smartreceipts.android.settings.UserPreferenceManager;
import dagger.Module;
import dagger.Provides;

@Module
public class FlavorModule {

    @Provides
    @ApplicationScope
    public static PurchaseWallet providePurchaseWallet(DefaultPurchaseWallet defaultPurchaseWallet) {
        return defaultPurchaseWallet;
    }

    @Provides
    @ApplicationScope
    public static ExtraInitializer provideExtraInitializer(ExtraInitializerFlossImpl flossInitializer) {
        return flossInitializer;
    }

    @Provides
    @ApplicationScope
    public static Analytics provideAnalytics(UserPreferenceManager userPreferenceManager) {
        return new AnalyticsManager(Arrays.asList(new AnalyticsLogger()), userPreferenceManager);
    }
}

package co.smartreceipts.android.ocr.widget.configuration;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import com.google.common.base.Preconditions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.inject.Inject;

import co.smartreceipts.android.activities.NavigationHandler;
import co.smartreceipts.android.di.scopes.FragmentScope;
import co.smartreceipts.android.identity.IdentityManager;
import co.smartreceipts.android.identity.store.EmailAddress;
import co.smartreceipts.android.ocr.purchases.OcrPurchaseTracker;
import co.smartreceipts.android.purchases.PurchaseManager;
import co.smartreceipts.android.purchases.model.AvailablePurchase;
import co.smartreceipts.android.purchases.model.PurchaseFamily;
import co.smartreceipts.android.purchases.source.PurchaseSource;
import co.smartreceipts.android.settings.UserPreferenceManager;
import co.smartreceipts.android.settings.catalog.UserPreference;
import co.smartreceipts.android.utils.log.Logger;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;


@FragmentScope
public class OcrConfigurationInteractor {

    private final NavigationHandler navigationHandler;
    private final IdentityManager identityManager;
    private final OcrPurchaseTracker ocrPurchaseTracker;
    private final PurchaseManager purchaseManager;
    private final UserPreferenceManager userPreferenceManager;

    @Inject
    public OcrConfigurationInteractor(OcrConfigurationFragment fragment, IdentityManager identityManager, OcrPurchaseTracker ocrPurchaseTracker,
                                      PurchaseManager purchaseManager, UserPreferenceManager userPreferenceManager) {
        this(new NavigationHandler(fragment), identityManager, ocrPurchaseTracker, purchaseManager, userPreferenceManager);
    }

    @VisibleForTesting
    OcrConfigurationInteractor(@NonNull NavigationHandler navigationHandler, @NonNull IdentityManager identityManager,
                               @NonNull OcrPurchaseTracker ocrPurchaseTracker, @NonNull PurchaseManager purchaseManager,
                               @NonNull UserPreferenceManager userPreferenceManager) {
        this.navigationHandler = Preconditions.checkNotNull(navigationHandler);
        this.identityManager = Preconditions.checkNotNull(identityManager);
        this.ocrPurchaseTracker = Preconditions.checkNotNull(ocrPurchaseTracker);
        this.purchaseManager = Preconditions.checkNotNull(purchaseManager);
        this.userPreferenceManager = Preconditions.checkNotNull(userPreferenceManager);
    }

    @Nullable
    public EmailAddress getEmail() {
        return identityManager.getEmail();
    }

    @NonNull
    public Observable<Integer> getRemainingScansStream() {
        return ocrPurchaseTracker.getRemainingScansStream()
                .observeOn(AndroidSchedulers.mainThread());
    }

    @NonNull
    public Observable<List<AvailablePurchase>> getAvailableOcrPurchases() {
        return purchaseManager.getAllAvailablePurchases()
                .map(availablePurchases -> {
                        final List<AvailablePurchase> ocrPurchases = new ArrayList<>();
                        for (final AvailablePurchase availablePurchase : availablePurchases) {
                            if (availablePurchase.getInAppPurchase() != null && PurchaseFamily.Ocr.equals(availablePurchase.getInAppPurchase().getPurchaseFamily())) {
                                ocrPurchases.add(availablePurchase);
                            }
                        }
                        Collections.sort(ocrPurchases, new Comparator<AvailablePurchase>() {
                            @Override
                            public int compare(AvailablePurchase purchase1, AvailablePurchase purchase2) {
                                return new BigDecimal(purchase1.getPriceAmountMicros()).compareTo(new BigDecimal(purchase2.getPriceAmountMicros()));
                            }
                        });
                        return ocrPurchases;
                })
                .observeOn(AndroidSchedulers.mainThread());
    }

    public void startOcrPurchase(@NonNull AvailablePurchase availablePurchase) {
        if (availablePurchase.getInAppPurchase() != null) {
            purchaseManager.initiatePurchase(availablePurchase.getInAppPurchase(), PurchaseSource.Ocr);
        } else {
            Logger.error(this, "Unexpected state in which the in app purchase is null");
        }
    }

    public boolean getAllowUsToSaveImagesRemotely() {
        return !userPreferenceManager.get(UserPreference.Misc.OcrIncognitoMode);
    }

    public void setAllowUsToSaveImagesRemotely(boolean saveImagesRemotely) {
        userPreferenceManager.set(UserPreference.Misc.OcrIncognitoMode, !saveImagesRemotely);
    }

    public void routeToProperLocation(@Nullable Bundle savedInstanceState) {
        if (!identityManager.isLoggedIn()) {
            if (savedInstanceState == null) {
                Logger.info(this, "User not logged in. Sending to the log in screen");
                navigationHandler.navigateToLoginScreen();
            } else {
                Logger.info(this, "Returning to this fragment after not signing in. Navigating back rather than looping back to the log in screen");
                navigateBack();
            }
        } else {
            Logger.debug(this, "User is already logged in. Doing nothing and remaining on this screen");
        }
    }

    public boolean navigateBack() {
        return this.navigationHandler.navigateBack();
    }

}

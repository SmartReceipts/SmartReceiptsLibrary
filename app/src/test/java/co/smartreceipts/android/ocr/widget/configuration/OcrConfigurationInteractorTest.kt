package co.smartreceipts.android.ocr.widget.configuration;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.billingclient.api.SkuDetails;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import co.smartreceipts.analytics.Analytics;
import co.smartreceipts.android.ocr.purchases.OcrPurchaseTracker;
import co.smartreceipts.android.purchases.PurchaseManager;
import co.smartreceipts.android.purchases.model.InAppPurchase;
import co.smartreceipts.android.purchases.source.PurchaseSource;
import co.smartreceipts.android.settings.UserPreferenceManager;
import co.smartreceipts.android.settings.catalog.UserPreference;
import co.smartreceipts.core.identity.IdentityManager;
import co.smartreceipts.core.identity.store.EmailAddress;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

public class OcrConfigurationInteractorTest {

    private final IdentityManager identityManager = mock(IdentityManager.class);

    private final OcrPurchaseTracker ocrPurchaseTracker = mock(OcrPurchaseTracker.class);

    private final PurchaseManager purchaseManager = mock(PurchaseManager.class);

    private final UserPreferenceManager userPreferenceManager = mock(UserPreferenceManager.class);

    private final Analytics analytics = mock(Analytics.class);

    private final SkuDetails availablePurchaseSkuDetails = mock(SkuDetails.class);

    private final SkuDetails availablePurchaseSkuDetails2 = mock(SkuDetails.class);

    private final OcrConfigurationInteractor interactor = new OcrConfigurationInteractor(
            identityManager,
            ocrPurchaseTracker,
            purchaseManager,
            userPreferenceManager,
            analytics,
            Schedulers.trampoline()
    );

    @Test
    public void getEmail() {
        final EmailAddress emailAddress = new EmailAddress("email");
        when(identityManager.getEmail()).thenReturn(emailAddress);
        assertEquals(emailAddress, interactor.getEmail());
    }

    @Test
    public void getRemainingScansStream() {
        final PublishSubject<Integer> scanSubject = PublishSubject.create();
        when(ocrPurchaseTracker.getRemainingScansStream()).thenReturn(scanSubject);

        TestObserver<Integer> testObserver = interactor.getRemainingScansStream().test();
        scanSubject.onNext(61);

        testObserver.assertValue(61);
        testObserver.assertNotComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void getAvailableOcrPurchasesOrdersByPrice() {
        when(availablePurchaseSkuDetails.getSku()).thenReturn(InAppPurchase.OcrScans50.getSku());
        when(availablePurchaseSkuDetails2.getSku()).thenReturn(InAppPurchase.OcrScans10.getSku());
        when(availablePurchaseSkuDetails.getPriceAmountMicros()).thenReturn(500000L);
        when(availablePurchaseSkuDetails2.getPriceAmountMicros()).thenReturn(100000L);

        final Set<SkuDetails> purchaseSet = new HashSet<>(Arrays.asList(availablePurchaseSkuDetails, availablePurchaseSkuDetails2));
        when(purchaseManager.getAllAvailablePurchaseSkus()).thenReturn(Single.just(purchaseSet));

        TestObserver<List<SkuDetails>> testObserver = interactor.getAvailableOcrPurchases().test();

        testObserver.awaitTerminalEvent();
        testObserver.assertValue(Arrays.asList(availablePurchaseSkuDetails2, availablePurchaseSkuDetails));
        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void getAvailableOcrPurchasesIgnoresSubscriptions() {
        when(availablePurchaseSkuDetails.getSku()).thenReturn(InAppPurchase.OcrScans50.getSku());
        when(availablePurchaseSkuDetails2.getSku()).thenReturn(InAppPurchase.SmartReceiptsPlus.getSku());
        when(availablePurchaseSkuDetails.getPriceAmountMicros()).thenReturn(500000L);
        when(availablePurchaseSkuDetails2.getPriceAmountMicros()).thenReturn(100000L);

        final Set<SkuDetails> purchaseSet = new HashSet<>(Arrays.asList(availablePurchaseSkuDetails, availablePurchaseSkuDetails2));
        when(purchaseManager.getAllAvailablePurchaseSkus()).thenReturn(Single.just(purchaseSet));

        TestObserver<List<SkuDetails>> testObserver = interactor.getAvailableOcrPurchases().test();

        testObserver.awaitTerminalEvent();
        testObserver.assertValue(Collections.singletonList(availablePurchaseSkuDetails));
        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void getAvailableOcrPurchasesIgnoresNonOcrOnes() {
        when(availablePurchaseSkuDetails.getSku()).thenReturn(InAppPurchase.OcrScans50.getSku());
        when(availablePurchaseSkuDetails2.getSku()).thenReturn(InAppPurchase.TestConsumablePurchase.getSku());
        when(availablePurchaseSkuDetails.getPriceAmountMicros()).thenReturn(500000L);
        when(availablePurchaseSkuDetails2.getPriceAmountMicros()).thenReturn(100000L);

        final Set<SkuDetails> purchaseSet = new HashSet<>(Arrays.asList(availablePurchaseSkuDetails, availablePurchaseSkuDetails2));
        when(purchaseManager.getAllAvailablePurchaseSkus()).thenReturn(Single.just(purchaseSet));

        TestObserver<List<SkuDetails>> testObserver = interactor.getAvailableOcrPurchases().test();

        testObserver.awaitTerminalEvent();
        testObserver.assertValue(Collections.singletonList(availablePurchaseSkuDetails));
        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void startOcrPurchase() {
        when(availablePurchaseSkuDetails.getSku()).thenReturn(InAppPurchase.OcrScans50.getSku());
        interactor.startOcrPurchase(availablePurchaseSkuDetails);
        verify(purchaseManager).initiatePurchase(availablePurchaseSkuDetails, PurchaseSource.Ocr);
    }

    @Test
    public void getOcrIsEnabled() {
        when(userPreferenceManager.getObservable(UserPreference.Misc.OcrIsEnabled)).thenReturn(Observable.just(false));
        final TestObserver<Boolean> testObserver1 = interactor.getOcrIsEnabled().test();
        testObserver1.awaitTerminalEvent();
        testObserver1.assertValue(false);
        testObserver1.assertComplete();
        testObserver1.assertNoErrors();

        when(userPreferenceManager.getObservable(UserPreference.Misc.OcrIsEnabled)).thenReturn(Observable.just(true));
        final TestObserver<Boolean> testObserver2 = interactor.getOcrIsEnabled().test();
        testObserver2.awaitTerminalEvent();
        testObserver2.assertValue(true);
        testObserver2.assertComplete();
        testObserver2.assertNoErrors();
    }

    @Test
    public void setOcrIsEnabled() {
        interactor.setOcrIsEnabled(false);
        verify(userPreferenceManager).set(UserPreference.Misc.OcrIsEnabled, false);

        interactor.setOcrIsEnabled(true);
        verify(userPreferenceManager).set(UserPreference.Misc.OcrIsEnabled, true);
    }

    @Test
    public void getAllowUsToSaveImagesRemotely() {
        when(userPreferenceManager.getObservable(UserPreference.Misc.OcrIncognitoMode)).thenReturn(Observable.just(true));
        final TestObserver<Boolean> testObserver1 = interactor.getAllowUsToSaveImagesRemotely().test();
        testObserver1.awaitTerminalEvent();
        testObserver1.assertValue(false);
        testObserver1.assertComplete();
        testObserver1.assertNoErrors();

        when(userPreferenceManager.getObservable(UserPreference.Misc.OcrIncognitoMode)).thenReturn(Observable.just(false));
        final TestObserver<Boolean> testObserver2 = interactor.getAllowUsToSaveImagesRemotely().test();
        testObserver2.awaitTerminalEvent();
        testObserver2.assertValue(true);
        testObserver2.assertComplete();
        testObserver2.assertNoErrors();
    }

    @Test
    public void setAllowUsToSaveImagesRemotely() {
        interactor.setAllowUsToSaveImagesRemotely(false);
        verify(userPreferenceManager).set(UserPreference.Misc.OcrIncognitoMode, true);

        interactor.setAllowUsToSaveImagesRemotely(true);
        verify(userPreferenceManager).set(UserPreference.Misc.OcrIncognitoMode, false);
    }

}
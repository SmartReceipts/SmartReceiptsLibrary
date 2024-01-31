package co.smartreceipts.android.ocr.widget.configuration

import co.smartreceipts.analytics.Analytics
import co.smartreceipts.android.config.ConfigurationManager
import co.smartreceipts.android.ocr.purchases.OcrPurchaseTracker
import co.smartreceipts.android.purchases.PurchaseManager
import co.smartreceipts.android.purchases.model.InAppPurchase
import co.smartreceipts.android.purchases.source.PurchaseSource
import co.smartreceipts.android.settings.UserPreferenceManager
import co.smartreceipts.android.settings.catalog.UserPreference
import co.smartreceipts.core.identity.IdentityManager
import co.smartreceipts.core.identity.store.EmailAddress
import com.android.billingclient.api.ProductDetails
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import java.util.Arrays

@RunWith(RobolectricTestRunner::class)
class OcrConfigurationInteractorTest {
    private val identityManager = Mockito.mock(IdentityManager::class.java)
    private val ocrPurchaseTracker = Mockito.mock(OcrPurchaseTracker::class.java)
    private val purchaseManager = Mockito.mock(PurchaseManager::class.java)
    private val userPreferenceManager = Mockito.mock(UserPreferenceManager::class.java)
    private val configurationManager = Mockito.mock(ConfigurationManager::class.java)
    private val analytics = Mockito.mock(Analytics::class.java)
    private val availablePurchaseSkuDetails = Mockito.mock(ProductDetails::class.java)
    private val availablePurchaseSkuDetails2 = Mockito.mock(ProductDetails::class.java)
    private val interactor = OcrConfigurationInteractor(
        identityManager,
        ocrPurchaseTracker,
        purchaseManager,
        userPreferenceManager,
        configurationManager,
        analytics,
        Schedulers.trampoline()
    )

    @Test
    fun getEmail() {
        val emailAddress = EmailAddress("email")
        whenever(identityManager.email).thenReturn(emailAddress)
        Assert.assertEquals(emailAddress, interactor.email)
    }

    @Test
    fun getRemainingScansStream() {
        val scanSubject = PublishSubject.create<Int>()
        whenever(ocrPurchaseTracker.remainingScansStream).thenReturn(scanSubject)
        val testObserver = interactor.getRemainingScansStream().test()
        scanSubject.onNext(61)
        testObserver.assertValue(61)
        testObserver.assertNotComplete()
        testObserver.assertNoErrors()
    }

    @Test
    fun getAvailableOcrPurchasesOrdersByPrice() {
        whenever(availablePurchaseSkuDetails.name).thenReturn(InAppPurchase.OcrScans50.sku)
        whenever(availablePurchaseSkuDetails2.name).thenReturn(InAppPurchase.OcrScans10.sku)
        whenever(availablePurchaseSkuDetails.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.priceAmountMicros)
            .thenReturn(500000L)
        whenever(availablePurchaseSkuDetails2.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.priceAmountMicros)
            .thenReturn(100000L)
        val purchaseSet: Set<ProductDetails> =
            HashSet(Arrays.asList(availablePurchaseSkuDetails, availablePurchaseSkuDetails2))
        whenever(purchaseManager.allAvailablePurchaseSkus).thenReturn(Single.just(purchaseSet))
        val testObserver = interactor.getAvailableOcrPurchases().test()
        testObserver.awaitTerminalEvent()
        testObserver.assertValue(Arrays.asList(availablePurchaseSkuDetails2, availablePurchaseSkuDetails))
        testObserver.assertComplete()
        testObserver.assertNoErrors()
    }

    @Test
    fun getAvailableOcrPurchasesIgnoresSubscriptions() {
        whenever(availablePurchaseSkuDetails.name).thenReturn(InAppPurchase.OcrScans50.sku)
        whenever(availablePurchaseSkuDetails2.name).thenReturn(InAppPurchase.SmartReceiptsPlus.sku)
        whenever(availablePurchaseSkuDetails.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.priceAmountMicros)
            .thenReturn(500000L)
        whenever(availablePurchaseSkuDetails2.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.priceAmountMicros)
            .thenReturn(100000L)
        val purchaseSet: Set<ProductDetails> =
            HashSet(Arrays.asList(availablePurchaseSkuDetails, availablePurchaseSkuDetails2))
        whenever(purchaseManager.allAvailablePurchaseSkus).thenReturn(Single.just(purchaseSet))
        val testObserver = interactor.getAvailableOcrPurchases().test()
        testObserver.awaitTerminalEvent()
        testObserver.assertValue(listOf(availablePurchaseSkuDetails))
        testObserver.assertComplete()
        testObserver.assertNoErrors()
    }

    @Test
    fun getAvailableOcrPurchasesIgnoresNonOcrOnes() {
        whenever(availablePurchaseSkuDetails.name).thenReturn(InAppPurchase.OcrScans50.sku)
        whenever(availablePurchaseSkuDetails2.name).thenReturn(InAppPurchase.TestConsumablePurchase.sku)
        whenever(availablePurchaseSkuDetails.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.priceAmountMicros)
            .thenReturn(500000L)
        whenever(availablePurchaseSkuDetails2.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.priceAmountMicros)
            .thenReturn(100000L)
        val purchaseSet: Set<ProductDetails> =
            HashSet(Arrays.asList(availablePurchaseSkuDetails, availablePurchaseSkuDetails2))
        whenever(purchaseManager.allAvailablePurchaseSkus).thenReturn(Single.just(purchaseSet))
        val testObserver = interactor.getAvailableOcrPurchases().test()
        testObserver.awaitTerminalEvent()
        testObserver.assertValue(listOf(availablePurchaseSkuDetails))
        testObserver.assertComplete()
        testObserver.assertNoErrors()
    }

    @Test
    fun startOcrPurchase() {
        whenever(availablePurchaseSkuDetails.name).thenReturn(InAppPurchase.OcrScans50.sku)
        interactor.startOcrPurchase(availablePurchaseSkuDetails)
        Mockito.verify(purchaseManager).initiatePurchase(availablePurchaseSkuDetails, PurchaseSource.Ocr)
    }

    @Test
    fun getOcrIsEnabled() {
        whenever(userPreferenceManager.getObservable(UserPreference.Misc.OcrIsEnabled))
            .thenReturn(Observable.just(false))
        val testObserver1 = interactor.getOcrIsEnabled().test()
        testObserver1.awaitTerminalEvent()
        testObserver1.assertValue(false)
        testObserver1.assertComplete()
        testObserver1.assertNoErrors()
        whenever(userPreferenceManager.getObservable(UserPreference.Misc.OcrIsEnabled))
            .thenReturn(Observable.just(true))
        val testObserver2 = interactor.getOcrIsEnabled().test()
        testObserver2.awaitTerminalEvent()
        testObserver2.assertValue(true)
        testObserver2.assertComplete()
        testObserver2.assertNoErrors()
    }

    @Test
    fun setOcrIsEnabled() {
        interactor.setOcrIsEnabled(false)
        Mockito.verify(userPreferenceManager).set(UserPreference.Misc.OcrIsEnabled, false)
        interactor.setOcrIsEnabled(true)
        Mockito.verify(userPreferenceManager).set(UserPreference.Misc.OcrIsEnabled, true)
    }

    @Test
    fun getAllowUsToSaveImagesRemotely() {
        whenever(userPreferenceManager.getObservable(UserPreference.Misc.OcrIncognitoMode))
            .thenReturn(Observable.just(true))
        val testObserver1 = interactor.getAllowUsToSaveImagesRemotely().test()
        testObserver1.awaitTerminalEvent()
        testObserver1.assertValue(false)
        testObserver1.assertComplete()
        testObserver1.assertNoErrors()
        whenever(userPreferenceManager.getObservable(UserPreference.Misc.OcrIncognitoMode))
            .thenReturn(Observable.just(false))
        val testObserver2 = interactor.getAllowUsToSaveImagesRemotely().test()
        testObserver2.awaitTerminalEvent()
        testObserver2.assertValue(true)
        testObserver2.assertComplete()
        testObserver2.assertNoErrors()
    }

    @Test
    fun setAllowUsToSaveImagesRemotely() {
        interactor.setAllowUsToSaveImagesRemotely(false)
        Mockito.verify(userPreferenceManager).set(UserPreference.Misc.OcrIncognitoMode, true)
        interactor.setAllowUsToSaveImagesRemotely(true)
        Mockito.verify(userPreferenceManager).set(UserPreference.Misc.OcrIncognitoMode, false)
    }
}
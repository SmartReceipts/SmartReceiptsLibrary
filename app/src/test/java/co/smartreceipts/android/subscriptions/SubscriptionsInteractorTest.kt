package co.smartreceipts.android.subscriptions

import co.smartreceipts.android.purchases.PurchaseEventsListener
import co.smartreceipts.android.purchases.PurchaseManager
import co.smartreceipts.android.purchases.model.InAppPurchase
import co.smartreceipts.android.purchases.model.ManagedProduct
import co.smartreceipts.android.purchases.source.PurchaseSource
import com.android.billingclient.api.ProductDetails
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SubscriptionsInteractorTest {

    // Class under test
    private lateinit var interactor: SubscriptionsInteractor

    private val purchaseManager = mock<PurchaseManager>()

    private val ocrPurchaseSkuDetails = mock<ProductDetails>()
    private val standardPlanSkuDetails = mock<ProductDetails>()
    private val premiumPlanSkuDetails = mock<ProductDetails>()

    private val standardPlanManagedProduct = mock<ManagedProduct>()

    private val purchaseListener = mock<PurchaseEventsListener>()

    @Before
    fun setUp() {
        whenever(ocrPurchaseSkuDetails.name).thenReturn("ocr_purchase_10")
        whenever(standardPlanSkuDetails.name).thenReturn("and_autorec_1month")
        whenever(premiumPlanSkuDetails.name).thenReturn("and_autorec_pro_1month")

        whenever(standardPlanManagedProduct.inAppPurchase).thenReturn(InAppPurchase.StandardSubscriptionPlan)

        whenever(purchaseManager.allOwnedPurchasesAndSync).thenReturn(Single.just(emptySet<ManagedProduct>()))
        whenever(purchaseManager.allAvailablePurchaseSkus).thenReturn(
            Single.just(setOf(ocrPurchaseSkuDetails, standardPlanSkuDetails, premiumPlanSkuDetails))
        )

        interactor = SubscriptionsInteractor(
            purchaseManager,
            Schedulers.trampoline(),
            Schedulers.trampoline()
        )
    }

    @Test
    fun getPlansWithOwnershipNoOwnedTest() {
        interactor.getPlansWithOwnership().test()
            .assertComplete()
            .assertNoErrors()
            .assertResult(mapOf(Pair(standardPlanSkuDetails, false), Pair(premiumPlanSkuDetails, false)))
    }

    @Test
    fun getPlansWithOwnershipOneOwnedTest() {
        whenever(purchaseManager.allOwnedPurchasesAndSync).thenReturn(Single.just(setOf(standardPlanManagedProduct)))

        interactor.getPlansWithOwnership().test()
            .assertComplete()
            .assertNoErrors()
            .assertResult(mapOf(Pair(standardPlanSkuDetails, true), Pair(premiumPlanSkuDetails, false)))
    }

    @Test
    fun purchaseStandardPlanTest() {
        interactor.purchaseStandardPlan()

        verify(purchaseManager).initiatePurchase(InAppPurchase.StandardSubscriptionPlan, PurchaseSource.SubscriptionsScreen)
        verify(purchaseManager, never()).initiatePurchase(InAppPurchase.PremiumSubscriptionPlan, PurchaseSource.SubscriptionsScreen)
    }

    @Test
    fun purchasePremiumPlanTest() {
        interactor.purchasePremiumPlan()

        verify(purchaseManager).initiatePurchase(InAppPurchase.PremiumSubscriptionPlan, PurchaseSource.SubscriptionsScreen)
        verify(purchaseManager, never()).initiatePurchase(InAppPurchase.StandardSubscriptionPlan, PurchaseSource.SubscriptionsScreen)
    }

    @Test
    fun addSubscriptionListenerTest() {
        interactor.addSubscriptionListener(purchaseListener)

        verify(purchaseManager).addEventListener(purchaseListener)
    }

    @Test
    fun removeSubscriptionListenerTest() {
        interactor.removeSubscriptionListener(purchaseListener)

        verify(purchaseManager).removeEventListener(purchaseListener)
    }
}
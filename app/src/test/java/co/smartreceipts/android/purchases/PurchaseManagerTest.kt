package co.smartreceipts.android.purchases

import android.app.Activity
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import co.smartreceipts.analytics.Analytics
import co.smartreceipts.android.purchases.model.InAppPurchase
import co.smartreceipts.android.purchases.model.ManagedProduct
import co.smartreceipts.android.purchases.source.PurchaseSource
import com.android.billingclient.api.ProductDetails
import com.nhaarman.mockitokotlin2.*
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PurchaseManagerTest {

    // Class under test
    private lateinit var purchaseManager: PurchaseManager

    private val billiClientManager = mock<BillingClientManager>()
    private val analytics = mock<Analytics>()

    private val ownedProduct = mock<ManagedProduct>()
    private val skuOcr10 = mock<ProductDetails>()
    private val skuOcr50 = mock<ProductDetails>()

    private val activity = mock<Activity>()


    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun setUp() {

        whenever(billiClientManager.queryAllOwnedPurchasesAndSync()).thenReturn(Single.just(setOf(ownedProduct)))
        whenever(billiClientManager.queryAllAvailablePurchases()).thenReturn(Single.just(setOf(skuOcr10, skuOcr50)))
        whenever(billiClientManager.querySkuDetails(InAppPurchase.OcrScans10)).thenReturn(Single.just(skuOcr10))
        whenever(billiClientManager.querySkuDetails(InAppPurchase.OcrScans50)).thenReturn(Single.just(skuOcr50))
        whenever(billiClientManager.initiatePurchase(any(), any())).thenReturn(Completable.complete())


        whenever(skuOcr10.name).thenReturn(InAppPurchase.OcrScans10.sku)
        whenever(skuOcr50.name).thenReturn(InAppPurchase.OcrScans50.sku)


        purchaseManager = PurchaseManager(billiClientManager, analytics, Schedulers.trampoline())

        purchaseManager.onActivityResumed(activity)
    }

    @Test
    fun initializationTest() {
        purchaseManager.initialize(application)

        verify(billiClientManager, only()).queryAllOwnedPurchasesAndSync()
    }

    @Test
    fun getAllOwnedPurchasesTest() {

        purchaseManager.allOwnedPurchasesAndSync.test()
            .assertComplete()
            .assertNoErrors()
            .assertResult(setOf(ownedProduct))

        verify(billiClientManager, only()).queryAllOwnedPurchasesAndSync()
    }

    @Test
    fun getAllOwnedPurchasesErrorTest() {
        whenever(billiClientManager.queryAllOwnedPurchasesAndSync()).thenReturn(Single.error(Exception("error")))

        purchaseManager.allOwnedPurchasesAndSync.test()
            .assertNotComplete()
            .assertError(Exception::class.java)

        verify(billiClientManager, only()).queryAllOwnedPurchasesAndSync()
    }

    @Test
    fun getAllAvailablePurchaseSkusTest() {

        purchaseManager.allAvailablePurchaseSkus.test()
            .assertComplete()
            .assertNoErrors()
            .assertResult(setOf(skuOcr10, skuOcr50))

        verify(billiClientManager, only()).queryAllAvailablePurchases()
    }

    @Test
    fun getAllAvailablePurchaseSkusErrorTest() {
        whenever(billiClientManager.queryAllAvailablePurchases()).thenReturn(Single.error(Exception("error")))

        purchaseManager.allAvailablePurchaseSkus.test()
            .assertNotComplete()
            .assertError(Exception::class.java)

        verify(billiClientManager, only()).queryAllAvailablePurchases()
    }

    @Test
    fun getAllAvailablePurchasesTest() {

        purchaseManager.allAvailablePurchases.test()
            .assertComplete()
            .assertResult(setOf(InAppPurchase.OcrScans10, InAppPurchase.OcrScans50))

        verify(billiClientManager, only()).queryAllAvailablePurchases()
    }

    @Test
    fun getAllAvailablePurchasesErrorTest() {
        whenever(billiClientManager.queryAllAvailablePurchases()).thenReturn(Single.error(Exception("error")))

        purchaseManager.allAvailablePurchases.test()
            .assertNotComplete()
            .assertError(Exception::class.java)

        verify(billiClientManager, only()).queryAllAvailablePurchases()
    }

    @Test
    fun initPurchaseTest() {

        purchaseManager.initiatePurchase(InAppPurchase.OcrScans10, PurchaseSource.Unknown)

        verify(billiClientManager).querySkuDetails(InAppPurchase.OcrScans10)
        verify(billiClientManager).initiatePurchase(eq(skuOcr10), any())
    }

    @Test
    fun initPurchaseBySkuTest() {

        purchaseManager.initiatePurchase(skuOcr10, PurchaseSource.Unknown)

        verify(billiClientManager, only()).initiatePurchase(eq(skuOcr10), any())
    }


}
package co.smartreceipts.android.ocr.widget.configuration

import co.smartreceipts.analytics.Analytics
import co.smartreceipts.analytics.events.DataPoint
import co.smartreceipts.analytics.events.DefaultDataPointEvent
import co.smartreceipts.analytics.events.Events
import co.smartreceipts.analytics.log.Logger
import co.smartreceipts.android.ocr.purchases.OcrPurchaseTracker
import co.smartreceipts.android.purchases.PurchaseManager
import co.smartreceipts.android.purchases.model.ConsumablePurchase
import co.smartreceipts.android.purchases.model.InAppPurchase
import co.smartreceipts.android.purchases.model.PurchaseFamily
import co.smartreceipts.android.purchases.source.PurchaseSource
import co.smartreceipts.android.settings.UserPreferenceManager
import co.smartreceipts.android.settings.catalog.UserPreference
import co.smartreceipts.android.utils.rx.RxSchedulers
import co.smartreceipts.core.di.scopes.FragmentScope
import co.smartreceipts.core.identity.IdentityManager
import co.smartreceipts.core.identity.store.EmailAddress
import com.android.billingclient.api.SkuDetails
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.Single
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Named

@FragmentScope
class OcrConfigurationInteractor @Inject constructor(
    private val identityManager: IdentityManager,
    private val ocrPurchaseTracker: OcrPurchaseTracker,
    private val purchaseManager: PurchaseManager,
    private val userPreferenceManager: UserPreferenceManager,
    private val analytics: Analytics,
    @Named(RxSchedulers.MAIN)
    private val observeOnScheduler: Scheduler
) {

    val email: EmailAddress?
        get() = identityManager.email

    val isUserLoggedIn: Boolean
        get() = identityManager.isLoggedIn


    fun getRemainingScansStream(): Observable<Int> {
        return ocrPurchaseTracker.remainingScansStream
            .observeOn(observeOnScheduler)
    }

    fun getAvailableOcrPurchases(): Single<List<SkuDetails>> {
        return purchaseManager.allAvailablePurchaseSkus
            .flatMapIterable { it }
            .filter { skuDetails ->
                val inAppPurchase = InAppPurchase.from(skuDetails.sku)
                inAppPurchase != null && inAppPurchase.type == ConsumablePurchase::class.java
                        && inAppPurchase.purchaseFamilies.contains(PurchaseFamily.Ocr)
            }
            .toSortedList { purchase1, purchase2 ->
                BigDecimal.valueOf(purchase1.priceAmountMicros)
                    .compareTo(BigDecimal.valueOf(purchase2.priceAmountMicros))
            }
            .observeOn(observeOnScheduler)
    }

    fun getOcrIsEnabled(): Observable<Boolean> = userPreferenceManager.getObservable(UserPreference.Misc.OcrIsEnabled)

    fun getAllowUsToSaveImagesRemotely(): Observable<Boolean> {
        return userPreferenceManager.getObservable(UserPreference.Misc.OcrIncognitoMode)
            .map { incognito -> !incognito }
    }


    fun startOcrPurchase(inAppPurchase: InAppPurchase?) {
        if (inAppPurchase != null) {
            analytics.record(
                DefaultDataPointEvent(Events.Ocr.OcrPurchaseClicked).addDataPoint(
                    DataPoint("sku", inAppPurchase)
                )
            )
            purchaseManager.initiatePurchase(inAppPurchase, PurchaseSource.Ocr)
        } else {
            Logger.error(this, "Unexpected state in which the in app purchase is null")
        }
    }

    fun startOcrPurchase(skuDetails: SkuDetails) {
        analytics.record(DefaultDataPointEvent(Events.Ocr.OcrPurchaseClicked).addDataPoint(DataPoint("sku", skuDetails)))
        purchaseManager.initiatePurchase(skuDetails, PurchaseSource.Ocr)
    }

    fun logOut() = identityManager.logOut()

    fun setOcrIsEnabled(ocrIsEnabled: Boolean) {
        analytics.record(
            DefaultDataPointEvent(Events.Ocr.OcrIsEnabledToggled).addDataPoint(
                DataPoint("value", ocrIsEnabled)
            )
        )
        userPreferenceManager[UserPreference.Misc.OcrIsEnabled] = ocrIsEnabled
    }

    fun setAllowUsToSaveImagesRemotely(saveImagesRemotely: Boolean) {
        analytics.record(
            DefaultDataPointEvent(Events.Ocr.OcrIncognitoModeToggled).addDataPoint(
                DataPoint("value", !saveImagesRemotely)
            )
        )
        userPreferenceManager[UserPreference.Misc.OcrIncognitoMode] = !saveImagesRemotely
    }

}

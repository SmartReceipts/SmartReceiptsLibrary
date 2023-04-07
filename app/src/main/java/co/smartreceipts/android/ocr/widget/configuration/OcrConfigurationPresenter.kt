package co.smartreceipts.android.ocr.widget.configuration

import co.smartreceipts.analytics.log.Logger
import co.smartreceipts.android.purchases.model.InAppPurchase
import co.smartreceipts.android.widget.viper.BaseViperPresenter
import co.smartreceipts.core.di.scopes.FragmentScope
import javax.inject.Inject

@FragmentScope
class OcrConfigurationPresenter @Inject
constructor(view: OcrConfigurationView, interactor: OcrConfigurationInteractor) :
    BaseViperPresenter<OcrConfigurationView, OcrConfigurationInteractor>(view, interactor) {

    override fun subscribe() {
        view.present(interactor.email)

        // Set the current checkbox values
        compositeDisposable.add(
            interactor.getOcrIsEnabled()
                .subscribe(view.ocrIsEnabledConsumer)
        )
        compositeDisposable.add(
            interactor.getAllowUsToSaveImagesRemotely()
                .subscribe(view.allowUsToSaveImagesRemotelyConsumer)
        )

        // Persist values from checkbox toggling
        compositeDisposable.add(view.ocrIsEnabledCheckboxStream
            .doOnNext { Logger.debug(this, "Updating ocrIsEnabled setting: {}", it) }
            .subscribe { interactor.setOcrIsEnabled(it) }
        )
        compositeDisposable.add(view.allowUsToSaveImagesRemotelyCheckboxStream
            .doOnNext { Logger.debug(this, "Updating saveImagesRemotely setting: {}", it) }
            .subscribe { interactor.setAllowUsToSaveImagesRemotely(it) }
        )

        // Show remaining scans if logged in
        compositeDisposable.add(interactor.getRemainingScansStream()
            .subscribe { view.present(it, interactor.isUserLoggedIn) }
        )

        // Show available purchases list
        compositeDisposable.add(interactor.getAvailableOcrPurchases()
            .doOnSuccess { Logger.info(this, "Presenting list of purchases: {}.", it) }
            .subscribe({ view.present(it) },
                { throwable -> Logger.warn(this, "Failed to get available purchases.", throwable) })
        )

        // Track user purchase clicks
        compositeDisposable.add(view.availablePurchaseClicks
            .doOnNext { Logger.info(this, "User clicked to buy purchase: {}.", it) }
            .subscribe { skuDetails ->
                if (interactor.isUserLoggedIn) {
                    interactor.startOcrPurchase(skuDetails)
                } else {
                    view.delayPurchaseAndPresentNeedToLogin(skuDetails.sku)
                }
            })

        compositeDisposable.add(view.delayedPurchaseIdStream
            .subscribe { purchaseId ->
                if (interactor.isUserLoggedIn) {
                    interactor.startOcrPurchase(InAppPurchase.from(purchaseId))
                }
            })
    }

}

package co.smartreceipts.android.ocr.widget.tooltip

import android.content.Context
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import co.smartreceipts.analytics.Analytics
import co.smartreceipts.analytics.events.Events
import co.smartreceipts.analytics.log.Logger
import co.smartreceipts.android.R
import co.smartreceipts.android.ocr.purchases.OcrPurchaseTracker
import co.smartreceipts.android.tooltip.TooltipController
import co.smartreceipts.android.tooltip.TooltipView
import co.smartreceipts.android.tooltip.model.TooltipInteraction
import co.smartreceipts.android.tooltip.model.TooltipMetadata
import co.smartreceipts.android.tooltip.model.TooltipType
import co.smartreceipts.android.utils.rx.RxSchedulers
import co.smartreceipts.core.di.scopes.FragmentScope
import co.smartreceipts.core.identity.IdentityManager
import com.hadisatrio.optional.Optional
import io.reactivex.Completable
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.functions.Consumer
import javax.inject.Inject
import javax.inject.Named

/**
 * An implementation of the [TooltipController] contract to a hint to the user about attempting to
 * recover a previous automatic backup
 */
@FragmentScope
class OcrInformationTooltipController @Inject constructor(private val context: Context,
                                                          private val tooltipView: TooltipView,
                                                          private val router: OcrInformationTooltipRouter,
                                                          private val interactor: OcrInformationalTooltipInteractor,
                                                          private val ocrPurchaseTracker: OcrPurchaseTracker,
                                                          private val analytics: Analytics,
                                                          private val identityManager: IdentityManager,
                                                          @Named(RxSchedulers.IO) private val scheduler: Scheduler) : TooltipController {

    @UiThread
    override fun shouldDisplayTooltip(): Single<Optional<TooltipMetadata>> {
        return interactor.showOcrTooltip
                .map { Optional.of(it) }
                .first(Optional.absent<OcrTooltipMessageType>())
                .map {
                    if (it.isPresent) {
                        return@map Optional.of(newTooltipMetadata(it.get()))
                    } else {
                        return@map Optional.absent<TooltipMetadata>()
                    }
                }.subscribeOn(scheduler)
    }

    @AnyThread
    override fun handleTooltipInteraction(interaction: TooltipInteraction): Completable {
        return Completable.fromCallable {
            interactor.markTooltipInteraction()
            if (interaction == TooltipInteraction.CloseCancelButtonClick) {
                analytics.record(Events.Ocr.OcrInfoTooltipDismiss)
            } else if (interaction == TooltipInteraction.TooltipClick) {
                analytics.record(Events.Ocr.OcrInfoTooltipOpen)
            }
            Logger.info(this@OcrInformationTooltipController, "User interacted the ocr information tooltip: {}", interaction)
        }.subscribeOn(scheduler)
    }

    @UiThread
    override fun consumeTooltipInteraction(): Consumer<TooltipInteraction> {
        return Consumer {
            tooltipView.hideTooltip()
            if (it == TooltipInteraction.TooltipClick) {
                if(identityManager.isLoggedIn)
                router.navigateToOcrConfigurationScreen()
                else router.navigationHandler.navigateToLoginScreen(true)
            }
        }
    }

    private fun newTooltipMetadata(ocrTooltipMessageType: OcrTooltipMessageType) : TooltipMetadata {
        return when (ocrTooltipMessageType) {
            OcrTooltipMessageType.NotConfigured -> TooltipMetadata(TooltipType.OcrInformation, context.getString(R.string.ocr_informational_tooltip_configure_text))
            OcrTooltipMessageType.LimitedScansRemaining, OcrTooltipMessageType.NoScansRemaining -> {
                val remainingScans = ocrPurchaseTracker.remainingScans
                TooltipMetadata(TooltipType.OcrInformation, context.resources.getQuantityString(R.plurals.ocr_informational_tooltip_limited_scans_text, remainingScans, remainingScans))
            }
        }
    }

}

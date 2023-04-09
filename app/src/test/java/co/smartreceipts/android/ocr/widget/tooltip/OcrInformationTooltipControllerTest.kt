package co.smartreceipts.android.ocr.widget.tooltip

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import co.smartreceipts.analytics.Analytics
import co.smartreceipts.analytics.events.Events
import co.smartreceipts.android.R
import co.smartreceipts.android.ocr.purchases.OcrPurchaseTracker
import co.smartreceipts.android.tooltip.TooltipView
import co.smartreceipts.android.tooltip.model.TooltipInteraction
import co.smartreceipts.android.tooltip.model.TooltipMetadata
import co.smartreceipts.android.tooltip.model.TooltipType
import co.smartreceipts.core.identity.IdentityManager
import com.hadisatrio.optional.Optional
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OcrInformationTooltipControllerTest {

    companion object {
        private val NOT_CONFIGURED_TOOLTIP_METADATA = TooltipMetadata(TooltipType.OcrInformation, ApplicationProvider.getApplicationContext<Context>().getString(R.string.ocr_informational_tooltip_configure_text))
        private val NO_SCANS_TOOLTIP_METADATA = TooltipMetadata(TooltipType.OcrInformation, ApplicationProvider.getApplicationContext<Context>().resources.getQuantityString(R.plurals.ocr_informational_tooltip_limited_scans_text, 0, 0))
        private val THREE_SCANS_TOOLTIP_METADATA = TooltipMetadata(TooltipType.OcrInformation, ApplicationProvider.getApplicationContext<Context>().resources.getQuantityString(R.plurals.ocr_informational_tooltip_limited_scans_text, 3, 3))
    }

    private lateinit var ocrInformationTooltipController: OcrInformationTooltipController

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Mock
    private lateinit var tooltipView: TooltipView

    @Mock
    private lateinit var router: OcrInformationTooltipRouter

    @Mock
    private lateinit var interactor: OcrInformationalTooltipInteractor

    @Mock
    private lateinit var ocrPurchaseTracker: OcrPurchaseTracker

    @Mock
    private lateinit var analytics: Analytics

    @Mock
    private lateinit var identityManager: IdentityManager

    private val scheduler = Schedulers.trampoline()

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        ocrInformationTooltipController = OcrInformationTooltipController(context, tooltipView, router, interactor, ocrPurchaseTracker, analytics, identityManager, scheduler)
    }

    @Test
    fun displayTooltipForNotConfiguredTooltip() {
        whenever(interactor.showOcrTooltip).thenReturn(Observable.just(OcrTooltipMessageType.NotConfigured))
        whenever(ocrPurchaseTracker.remainingScans).thenReturn(0)
        ocrInformationTooltipController.shouldDisplayTooltip()
                .test()
                .await()
                .assertValue(Optional.of(NOT_CONFIGURED_TOOLTIP_METADATA))
                .assertComplete()
                .assertNoErrors()
    }

    @Test
    fun displayTooltipForNoScansRemainingTooltip() {
        whenever(interactor.showOcrTooltip).thenReturn(Observable.just(OcrTooltipMessageType.NoScansRemaining))
        whenever(ocrPurchaseTracker.remainingScans).thenReturn(0)
        ocrInformationTooltipController.shouldDisplayTooltip()
                .test()
                .await()
                .assertValue(Optional.of(NO_SCANS_TOOLTIP_METADATA))
                .assertComplete()
                .assertNoErrors()
    }

    @Test
    fun displayTooltipForLimitedScansRemainingTooltip() {
        whenever(interactor.showOcrTooltip).thenReturn(Observable.just(OcrTooltipMessageType.LimitedScansRemaining))
        whenever(ocrPurchaseTracker.remainingScans).thenReturn(3)
        ocrInformationTooltipController.shouldDisplayTooltip()
                .test()
                .await()
                .assertValue(Optional.of(THREE_SCANS_TOOLTIP_METADATA))
                .assertComplete()
                .assertNoErrors()
    }

    @Test
    fun doNotDisplayTooltipForEmptyOcrTooltip() {
        whenever(interactor.showOcrTooltip).thenReturn(Observable.empty())
        ocrInformationTooltipController.shouldDisplayTooltip()
                .test()
                .await()
                .assertValue(Optional.absent())
                .assertComplete()
                .assertNoErrors()
    }

    @Test
    fun handleTooltipClickInteraction() {
        val interaction = TooltipInteraction.TooltipClick
        ocrInformationTooltipController.handleTooltipInteraction(interaction)
                .test()
                .await()
                .assertComplete()
                .assertNoErrors()

        verify(interactor).markTooltipInteraction()
        verify(analytics).record(Events.Ocr.OcrInfoTooltipOpen)
    }

    @Test
    fun handleCloseCancelButtonClickInteraction() {
        val interaction = TooltipInteraction.CloseCancelButtonClick
        ocrInformationTooltipController.handleTooltipInteraction(interaction)
                .test()
                .await()
                .assertComplete()
                .assertNoErrors()

        verify(interactor).markTooltipInteraction()
        verify(analytics).record(Events.Ocr.OcrInfoTooltipDismiss)
    }

    @Test
    fun consumeTooltipClickInteraction() {
        ocrInformationTooltipController.consumeTooltipInteraction().accept(TooltipInteraction.TooltipClick)
        verify(tooltipView).hideTooltip()
        verify(router).navigateToOcrConfigurationScreen()
    }

    @Test
    fun consumeTooltipCloseInteraction() {
        ocrInformationTooltipController.consumeTooltipInteraction().accept(TooltipInteraction.CloseCancelButtonClick)
        verify(tooltipView).hideTooltip()
        verifyZeroInteractions(router)
    }

}
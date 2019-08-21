package co.smartreceipts.android.tooltip.image

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import co.smartreceipts.android.R
import co.smartreceipts.android.settings.UserPreferenceManager
import co.smartreceipts.android.settings.catalog.UserPreference
import co.smartreceipts.android.tooltip.TooltipView
import co.smartreceipts.android.tooltip.image.data.ImageCroppingPreferenceStorage
import co.smartreceipts.android.tooltip.model.TooltipInteraction
import co.smartreceipts.android.tooltip.model.TooltipMetadata
import co.smartreceipts.android.tooltip.model.TooltipType
import com.hadisatrio.optional.Optional
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Single
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImageCroppingTooltipControllerTest {

    lateinit var controller: ImageCroppingTooltipController

    @Mock
    lateinit var tooltipView: TooltipView

    @Mock
    lateinit var prefStorage: ImageCroppingPreferenceStorage

    @Mock
    lateinit var prefManager: UserPreferenceManager

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        controller = ImageCroppingTooltipController(ApplicationProvider.getApplicationContext(), tooltipView, prefStorage, prefManager)
    }

    @Test
    fun doNotDisplayTooltipIfCroppingWasNotShown() {
        whenever(prefStorage.getCroppingScreenWasShown()).thenReturn(Single.just(false))
        whenever(prefStorage.getCroppingTooltipWasHandled()).thenReturn(Single.just(false))

        controller.shouldDisplayTooltip().test()
            .assertComplete()
            .assertNoErrors()
            .assertValue(Optional.absent())
    }

    @Test
    fun doNotDisplayTooltipIfItWasAlreadyShown() {
        whenever(prefStorage.getCroppingScreenWasShown()).thenReturn(Single.just(true))
        whenever(prefStorage.getCroppingTooltipWasHandled()).thenReturn(Single.just(true))

        controller.shouldDisplayTooltip().test()
            .assertComplete()
            .assertNoErrors()
            .assertValue(Optional.absent())
    }

    @Test
    fun displayTooltip() {
        whenever(prefStorage.getCroppingScreenWasShown()).thenReturn(Single.just(true))
        whenever(prefStorage.getCroppingTooltipWasHandled()).thenReturn(Single.just(false))

        controller.shouldDisplayTooltip().test()
            .assertComplete()
            .assertNoErrors()
            .assertResult(Optional.of(TooltipMetadata(TooltipType.ImageCropping, ApplicationProvider.getApplicationContext<Context>().getString(R.string.pref_general_enable_crop_title))))
    }

    @Test
    fun handleYesTooltipClick() {
        val interaction = TooltipInteraction.YesButtonClick

        controller.handleTooltipInteraction(interaction).test()
            .await()
            .assertComplete()
            .assertNoErrors()

        verify(prefManager).set(UserPreference.General.EnableCrop, true)
        verify(prefStorage).setCroppingTooltipWasHandled(true)
    }

    @Test
    fun handleNoTooltipClick() {
        val interaction = TooltipInteraction.NoButtonClick

        controller.handleTooltipInteraction(interaction).test()
            .await()
            .assertComplete()
            .assertNoErrors()

        verify(prefManager).set(UserPreference.General.EnableCrop, false)
        verify(prefStorage).setCroppingTooltipWasHandled(true)
    }

    @Test
    fun consumeTooltipYes() {
        controller.consumeTooltipInteraction().accept(TooltipInteraction.YesButtonClick)

        verify(tooltipView).hideTooltip()
        verifyZeroInteractions(prefManager, prefStorage)
    }

    @Test
    fun consumeTooltipNo() {
        controller.consumeTooltipInteraction().accept(TooltipInteraction.NoButtonClick)

        verify(tooltipView).hideTooltip()
        verifyZeroInteractions(prefManager, prefStorage)
    }


}
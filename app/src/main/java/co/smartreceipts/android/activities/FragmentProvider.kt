package co.smartreceipts.android.activities

import android.os.Bundle
import androidx.fragment.app.Fragment
import co.smartreceipts.android.distance.editor.DistanceCreateEditFragment
import co.smartreceipts.android.distance.editor.DistanceCreateEditFragment.Companion.ARG_SUGGESTED_DATE
import co.smartreceipts.android.fragments.ReceiptImageFragment
import co.smartreceipts.android.fragments.ReportInfoFragment
import co.smartreceipts.android.identity.widget.account.AccountFragment
import co.smartreceipts.android.identity.widget.login.LoginFragment
import co.smartreceipts.android.model.Distance
import co.smartreceipts.android.model.Receipt
import co.smartreceipts.android.model.Trip
import co.smartreceipts.android.ocr.apis.model.OcrResponse
import co.smartreceipts.android.ocr.widget.configuration.OcrConfigurationFragment
import co.smartreceipts.android.receipts.editor.ReceiptCreateEditFragment
import co.smartreceipts.android.receipts.editor.ReceiptCreateEditFragment.ARG_FILE
import co.smartreceipts.android.receipts.editor.ReceiptCreateEditFragment.ARG_OCR
import co.smartreceipts.android.sync.widget.backups.BackupsFragment
import co.smartreceipts.android.trips.TripFragment
import co.smartreceipts.android.trips.editor.TripCreateEditFragment
import java.io.File
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList

class FragmentProvider @Inject constructor() {

    /**
     * Creates a [TripFragment] instance
     *
     * @return a new trip fragment
     */
    fun newTripFragmentInstance(): TripFragment {
        return TripFragment.newInstance()
    }

    /**
     * Creates a [ReportInfoFragment] instance
     *
     * @param trip the trip to display info for
     * @return a new report info fragment
     */
    fun newReportInfoFragment(trip: Trip): ReportInfoFragment {
        val args = Bundle()
        args.putParcelable(Trip.PARCEL_KEY, trip)

        return attachArguments(ReportInfoFragment.newInstance(), args)
    }

    /**
     * Creates a [ReceiptCreateEditFragment] for a new receipt
     *
     * @param trip the parent trip of this receipt
     * @param file the file associated with this receipt or null if we do not have one
     * @return the new instance of this fragment
     */
    fun newCreateReceiptFragment(trip: Trip, file: File?, ocrResponse: OcrResponse?): ReceiptCreateEditFragment {
        val args = Bundle()
        args.putParcelable(Trip.PARCEL_KEY, trip)
        args.putParcelable(Receipt.PARCEL_KEY, null)
        args.putSerializable(ARG_FILE, file)
        args.putSerializable(ARG_OCR, ocrResponse)

        return attachArguments(ReceiptCreateEditFragment.newInstance(), args)
    }

    /**
     * Creates a [ReceiptCreateEditFragment] to edit an existing receipt
     *
     * @param trip the parent trip of this receipt
     * @param receiptToEdit the receipt to edit
     * @return the new instance of this fragment
     */
    fun newEditReceiptFragment(trip: Trip, receiptToEdit: Receipt): ReceiptCreateEditFragment {
        val args = Bundle()
        args.putParcelable(Trip.PARCEL_KEY, trip)
        args.putParcelable(Receipt.PARCEL_KEY, receiptToEdit)
        args.putSerializable(ARG_FILE, null)
        args.putSerializable(ARG_OCR, null)

        return attachArguments(ReceiptCreateEditFragment.newInstance(), args)
    }

    /**
     * Creates a [ReceiptImageFragment] instance
     *
     * @param receipt the receipt to show the image for
     * @return a new instance of this fragment
     */
    fun newReceiptImageFragment(receipt: Receipt): ReceiptImageFragment {
        val args = Bundle()
        args.putParcelable(Receipt.PARCEL_KEY, receipt)

        return attachArguments(ReceiptImageFragment.newInstance(), args)
    }

    fun newCreateDistanceFragment(trip: Trip, suggestedDate: Date?): DistanceCreateEditFragment {
        val args = Bundle()
        args.putParcelable(Trip.PARCEL_KEY, trip)
        suggestedDate?.let { args.putLong(ARG_SUGGESTED_DATE, it.time + 1) }

        return attachArguments(DistanceCreateEditFragment.newInstance(), args)
    }

    fun newEditDistanceFragment(trip: Trip, distance: Distance): DistanceCreateEditFragment {
        val args = Bundle()
        args.putParcelable(Trip.PARCEL_KEY, trip)
        args.putParcelable(Distance.PARCEL_KEY, distance)

        return attachArguments(DistanceCreateEditFragment.newInstance(), args)
    }

    /**
     * Creates a [BackupsFragment] instance
     *
     * @return a new instance of this fragment
     */
    fun newBackupsFragment(): BackupsFragment {
        return BackupsFragment()
    }

    /**
     * Creates a [LoginFragment] instance
     *
     * @return a new instance of this fragment
     */
    @JvmOverloads fun newLoginFragment(isFromOcr:Boolean = false): LoginFragment {
        val args = Bundle()
        args.putBoolean(LoginFragment.IS_FROM_OCR,isFromOcr)
        return attachArguments(LoginFragment.newInstance(), args)
    }

    /**
     * Creates a [AccountFragment] instance
     *
     * @return a new instance of this fragment
     */
    fun newAccountFragment(): AccountFragment {
        return AccountFragment.newInstance()
    }

    /**
     * Creates a [OcrConfigurationFragment] instance
     *
     * @return a new instance of this fragment
     */
    fun newOcrConfigurationFragment(): OcrConfigurationFragment {
        return OcrConfigurationFragment.newInstance()
    }

    /**
     * Creates a [TripCreateEditFragment] for a new trip
     *
     * @return the new instance of this fragment
     */
    fun newCreateTripFragment(existingTrips: List<Trip>): TripCreateEditFragment {
        val args = Bundle()
        args.putParcelableArrayList(TripCreateEditFragment.ARG_EXISTING_TRIPS, ArrayList(existingTrips))
        return attachArguments(TripCreateEditFragment.newInstance(), args)
    }

    /**
     * Creates a [TripCreateEditFragment] to edit an existing trip
     *
     * @param tripToEdit the trip to edit
     * @return the new instance of this fragment
     */
    fun newEditTripFragment(tripToEdit: Trip): TripCreateEditFragment {
        // Note: Don't pass the list of existing trips here, since we can have a conflict on the same name when editing
        val args = Bundle()
        args.putParcelable(Trip.PARCEL_KEY, tripToEdit)
        return attachArguments(TripCreateEditFragment.newInstance(), args)
    }

    private fun <T : Fragment> attachArguments(fragment: T, args: Bundle): T {
        fragment.arguments = args
        return fragment
    }
}

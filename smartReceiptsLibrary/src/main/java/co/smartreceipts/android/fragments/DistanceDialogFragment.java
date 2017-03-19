package co.smartreceipts.android.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.Time;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Spinner;

import java.math.BigDecimal;
import java.sql.Date;

import javax.inject.Inject;

import co.smartreceipts.android.R;
import co.smartreceipts.android.SmartReceiptsApplication;
import co.smartreceipts.android.analytics.events.Events;
import co.smartreceipts.android.date.DateEditText;
import co.smartreceipts.android.date.DateManager;
import co.smartreceipts.android.model.Distance;
import co.smartreceipts.android.model.Trip;
import co.smartreceipts.android.model.factory.DistanceBuilderFactory;
import co.smartreceipts.android.model.utils.ModelUtils;
import co.smartreceipts.android.persistence.DatabaseHelper;
import co.smartreceipts.android.persistence.PersistenceManager;
import co.smartreceipts.android.persistence.database.controllers.impl.DistanceTableController;
import co.smartreceipts.android.persistence.database.operations.DatabaseOperationMetadata;
import co.smartreceipts.android.settings.UserPreferenceManager;
import co.smartreceipts.android.settings.catalog.UserPreference;
import dagger.android.support.AndroidSupportInjection;
import wb.android.autocomplete.AutoCompleteAdapter;

public class DistanceDialogFragment extends DialogFragment implements OnClickListener {

    public static final String TAG = DistanceDialogFragment.class.getSimpleName();
    private static final String ARG_SUGGESTED_DATE = "arg_suggested_date";

    @Inject
    PersistenceManager persistenceManager;

    @Inject
    DateManager dateManager;

    private EditText distance, rate, comment;
    private AutoCompleteTextView location;
    private DateEditText date;
    private Spinner currency;
    private Trip trip;
    private Distance updateableDistance;
    private AutoCompleteAdapter locationAutoCompleteAdapter;
    private Date suggestedDate;
    private DistanceTableController distanceTableController;

    /**
     * Creates a new instance of a {@link co.smartreceipts.android.fragments.DistanceDialogFragment}, which
     * can be used to enter a new distance item
     *
     * @param trip - the parent {@link co.smartreceipts.android.model.Trip}
     * @return - a {@link co.smartreceipts.android.fragments.DistanceDialogFragment}
     */
    public static DistanceDialogFragment newInstance(final Trip trip) {
        return newInstance(trip, null, null);
    }


    /**
     * Creates a new instance of a {@link co.smartreceipts.android.fragments.DistanceDialogFragment}, which
     * can be used to enter a new distance item
     *
     * @param trip          - the parent {@link co.smartreceipts.android.model.Trip}
     * @param suggestedDate - the suggested {@link java.sql.Date} to display to the user when creating a new distance item
     * @return - a {@link co.smartreceipts.android.fragments.DistanceDialogFragment}
     */
    public static DistanceDialogFragment newInstance(final Trip trip, final Date suggestedDate) {
        return newInstance(trip, null, suggestedDate);
    }


    /**
     * Creates a new instance of a {@link co.smartreceipts.android.fragments.DistanceDialogFragment}, which
     * can be used to update an existing distance item
     *
     * @param trip     - the parent {@link co.smartreceipts.android.model.Trip}
     * @param distance - the {@link co.smartreceipts.android.model.Distance} object to update
     * @return - a {@link co.smartreceipts.android.fragments.DistanceDialogFragment}
     */
    public static DistanceDialogFragment newInstance(final Trip trip, final Distance distance) {
        return newInstance(trip, distance, null);
    }


    private static DistanceDialogFragment newInstance(final Trip trip, final Distance distance, final Date suggestedDate) {
        final DistanceDialogFragment dialog = new DistanceDialogFragment();
        final Bundle args = new Bundle();
        args.putParcelable(Trip.PARCEL_KEY, trip);
        if (distance != null) {
            args.putParcelable(Distance.PARCEL_KEY, distance);
        }
        if (suggestedDate != null) {
            args.putLong(ARG_SUGGESTED_DATE, suggestedDate.getTime() + 1);
        }
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onAttach(Context context) {
        AndroidSupportInjection.inject(this);
        super.onAttach(context);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final SmartReceiptsApplication app = ((SmartReceiptsApplication) getActivity().getApplication());

        distanceTableController = app.getTableControllerManager().getDistanceTableController();
        trip = getArguments().getParcelable(Trip.PARCEL_KEY);
        updateableDistance = getArguments().getParcelable(Distance.PARCEL_KEY);
        final Time now = new Time();
        now.setToNow();
        // Default to "now" if not suggested date was set
        suggestedDate = new Date(getArguments().getLong(ARG_SUGGESTED_DATE, now.toMillis(false)));
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final LayoutInflater inflater = LayoutInflater.from(getActivity());
        final View rootView = inflater.inflate(R.layout.dialog_mileage, null);

        distance = (EditText) rootView.findViewById(R.id.dialog_mileage_distance);
        rate = (EditText) rootView.findViewById(R.id.dialog_mileage_rate);
        currency = (Spinner) rootView.findViewById(R.id.dialog_mileage_currency);
        location = (AutoCompleteTextView) rootView.findViewById(R.id.dialog_mileage_location);
        comment = (EditText) rootView.findViewById(R.id.dialog_mileage_comment);
        date = (DateEditText) rootView.findViewById(R.id.dialog_mileage_date);

        DatabaseHelper databaseHelper = persistenceManager.getDatabase();
        UserPreferenceManager prefs = persistenceManager.getPreferenceManager();

        final ArrayAdapter<CharSequence> currencies = new ArrayAdapter<CharSequence>(getActivity(),
                android.R.layout.simple_spinner_item, databaseHelper.getCurrenciesList());
        currencies.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        currency.setAdapter(currencies);
        date.setOnClickListener(dateManager.getDateEditTextListener());
        date.setFocusable(false);
        date.setFocusableInTouchMode(false);

        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(rootView);
        if (updateableDistance == null) {
            // New Distance
            builder.setTitle(getString(R.string.dialog_mileage_title_create));
            builder.setPositiveButton(getString(R.string.dialog_mileage_positive_create), this);
            date.date = suggestedDate;
            date.setText(DateFormat.getDateFormat(getActivity()).format(date.date));
            int idx = currencies.getPosition(trip.getPrice().getCurrencyCode());
            if (idx > 0) {
                currency.setSelection(idx);
            }
            final float distanceRate = prefs.get(UserPreference.Distance.DefaultDistanceRate);
            if (distanceRate > 0) {
                rate.setText(ModelUtils.getDecimalFormattedValue(new BigDecimal(distanceRate), Distance.RATE_PRECISION));
            }
            if (locationAutoCompleteAdapter == null) {
                locationAutoCompleteAdapter = AutoCompleteAdapter.getInstance(getActivity(),
                        DatabaseHelper.TAG_DISTANCE_LOCATION, databaseHelper);
            } else {
                locationAutoCompleteAdapter.reset();
            }
            location.setAdapter(locationAutoCompleteAdapter);
            distance.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus && getActivity() != null && getDialog() != null) {
                        if (getActivity().getResources().getConfiguration().hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES) {
                            getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                        }
                    }
                }
            });
        } else {
            // Update distance
            builder.setTitle(getString(R.string.dialog_mileage_title_update));
            builder.setPositiveButton(getString(R.string.dialog_mileage_positive_update), this);
            builder.setNeutralButton(getString(R.string.dialog_mileage_neutral_delete), this);
            distance.setText(updateableDistance.getDecimalFormattedDistance());
            rate.setText(updateableDistance.getDecimalFormattedRate());
            location.setText(updateableDistance.getLocation());
            comment.setText(updateableDistance.getComment());
            date.setText(updateableDistance.getFormattedDate(getActivity(), prefs.get(UserPreference.General.DateSeparator)));
            date.date = updateableDistance.getDate();
            int idx = currencies.getPosition(updateableDistance.getPrice().getCurrencyCode());
            if (idx > 0) {
                currency.setSelection(idx);
            }
        }
        builder.setNegativeButton(android.R.string.cancel, this);

        final Dialog dialog = builder.create();
        dateManager.setDateEditTextListenerDialogHolder(dialog);
        return dialog;
    }

    @Override
    public void onPause() {
        if (locationAutoCompleteAdapter != null) {
            locationAutoCompleteAdapter.onPause();
        }
        super.onPause();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            // Create/Edit
            final String currency = this.currency.getSelectedItem().toString();
            final String location = this.location.getText().toString();
            final String comment = this.comment.getText().toString();
            final Date date = this.date.date;

            if (updateableDistance == null) {
                // We're inserting a new one
                final BigDecimal distance = getBigDecimalFromString(this.distance.getText().toString(), new BigDecimal(0));
                final BigDecimal rate = getBigDecimalFromString(this.rate.getText().toString(), new BigDecimal(0));
                final DistanceBuilderFactory builder = new DistanceBuilderFactory();
                builder.setTrip(trip);
                builder.setLocation(location);
                builder.setDistance(distance);
                builder.setDate(date);
                builder.setRate(rate);
                builder.setCurrency(currency);
                builder.setComment(comment);
                ((SmartReceiptsApplication)getActivity().getApplication()).getAnalyticsManager().record(Events.Distance.PersistNewDistance);
                distanceTableController.insert(builder.build(), new DatabaseOperationMetadata());
            } else {
                // We're updating
                final BigDecimal distance = getBigDecimalFromString(this.distance.getText().toString(), updateableDistance.getDistance());
                final BigDecimal rate = getBigDecimalFromString(this.rate.getText().toString(), updateableDistance.getRate());
                final DistanceBuilderFactory builder = new DistanceBuilderFactory(updateableDistance);
                builder.setLocation(location);
                builder.setDistance(distance);
                builder.setDate(date);
                builder.setRate(rate);
                builder.setCurrency(currency);
                builder.setComment(comment);
                ((SmartReceiptsApplication)getActivity().getApplication()).getAnalyticsManager().record(Events.Distance.PersistUpdateDistance);
                distanceTableController.update(updateableDistance, builder.build(), new DatabaseOperationMetadata());
            }
        } else if (which == DialogInterface.BUTTON_NEUTRAL) {
            // Delete
            final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(getString(R.string.delete_item, updateableDistance.getLocation()));
            builder.setMessage(R.string.delete_sync_information);
            builder.setCancelable(true);
            builder.setPositiveButton(R.string.delete, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    distanceTableController.delete(updateableDistance, new DatabaseOperationMetadata());
                    dismiss();
                }
            });
            builder.setNegativeButton(android.R.string.cancel, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dismiss();
                }
            });
            builder.show();
        }
        dismiss();
    }

    /**
     * @param number   - a string containing a number
     * @param fallback - the {@link BigDecimal} to return if the string is NaN
     * @return a {@link BigDecimal} or the fallback param if not
     */
    private BigDecimal getBigDecimalFromString(String number, BigDecimal fallback) {
        if (TextUtils.isEmpty(number)) {
            return fallback;
        }
        try {
            return new BigDecimal(number.replace(",", "."));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

}

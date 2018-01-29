package co.smartreceipts.android.receipts.editor;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.Time;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.hadisatrio.optional.Optional;
import com.jakewharton.rxbinding2.view.RxView;
import com.jakewharton.rxbinding2.widget.RxDateEditText;
import com.jakewharton.rxbinding2.widget.RxTextView;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.BindViews;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import co.smartreceipts.android.R;
import co.smartreceipts.android.activities.NavigationHandler;
import co.smartreceipts.android.adapters.FooterButtonArrayAdapter;
import co.smartreceipts.android.adapters.TaxAutoCompleteAdapter;
import co.smartreceipts.android.analytics.Analytics;
import co.smartreceipts.android.analytics.events.Events;
import co.smartreceipts.android.currency.PriceCurrency;
import co.smartreceipts.android.currency.widget.CurrencyListEditorPresenter;
import co.smartreceipts.android.currency.widget.DefaultCurrencyListEditorView;
import co.smartreceipts.android.date.DateEditText;
import co.smartreceipts.android.date.DateManager;
import co.smartreceipts.android.fragments.ChildFragmentNavigationHandler;
import co.smartreceipts.android.fragments.ReceiptInputCache;
import co.smartreceipts.android.fragments.WBFragment;
import co.smartreceipts.android.model.Category;
import co.smartreceipts.android.model.PaymentMethod;
import co.smartreceipts.android.model.Price;
import co.smartreceipts.android.model.Receipt;
import co.smartreceipts.android.model.Trip;
import co.smartreceipts.android.model.gson.ExchangeRate;
import co.smartreceipts.android.model.impl.ImmutablePaymentMethodImpl;
import co.smartreceipts.android.model.utils.ModelUtils;
import co.smartreceipts.android.ocr.apis.model.OcrResponse;
import co.smartreceipts.android.ocr.util.OcrResponseParser;
import co.smartreceipts.android.ocr.widget.tooltip.OcrInformationalTooltipFragment;
import co.smartreceipts.android.persistence.DatabaseHelper;
import co.smartreceipts.android.persistence.database.controllers.TableEventsListener;
import co.smartreceipts.android.persistence.database.controllers.impl.CategoriesTableController;
import co.smartreceipts.android.persistence.database.controllers.impl.PaymentMethodsTableController;
import co.smartreceipts.android.persistence.database.controllers.impl.StubTableEventsListener;
import co.smartreceipts.android.receipts.editor.currency.ReceiptCurrencyCodeSupplier;
import co.smartreceipts.android.receipts.editor.date.ReceiptDateView;
import co.smartreceipts.android.receipts.editor.exchange.CurrencyExchangeRateEditorPresenter;
import co.smartreceipts.android.receipts.editor.exchange.CurrencyExchangeRateEditorView;
import co.smartreceipts.android.receipts.editor.exchange.ExchangeRateServiceManager;
import co.smartreceipts.android.receipts.editor.pricing.EditableReceiptPricingView;
import co.smartreceipts.android.receipts.editor.pricing.ReceiptPricingPresenter;
import co.smartreceipts.android.settings.UserPreferenceManager;
import co.smartreceipts.android.utils.SoftKeyboardManager;
import co.smartreceipts.android.utils.butterknife.ButterKnifeActions;
import co.smartreceipts.android.utils.log.Logger;
import co.smartreceipts.android.widget.NetworkRequestAwareEditText;
import co.smartreceipts.android.widget.model.UiIndicator;
import co.smartreceipts.android.widget.rxbinding2.RxTextViewExtensions;
import co.smartreceipts.android.widget.tooltip.report.backup.data.BackupReminderTooltipStorage;
import dagger.android.support.AndroidSupportInjection;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import wb.android.autocomplete.AutoCompleteAdapter;
import wb.android.flex.Flex;

import static java.util.Collections.emptyList;

public class ReceiptCreateEditFragment extends WBFragment implements View.OnFocusChangeListener,
        DatabaseHelper.ReceiptAutoCompleteListener,
        EditableReceiptPricingView,
        ReceiptDateView,
        CurrencyExchangeRateEditorView {

    public static final String ARG_FILE = "arg_file";
    public static final String ARG_OCR = "arg_ocr";

    @Inject
    Flex flex;

    @Inject
    DateManager dateManager;

    @Inject
    DatabaseHelper database;

    @Inject
    ExchangeRateServiceManager exchangeRateServiceManager;

    @Inject
    Analytics analytics;

    @Inject
    CategoriesTableController categoriesTableController;

    @Inject
    PaymentMethodsTableController paymentMethodsTableController;

    @Inject
    NavigationHandler navigationHandler;

    @Inject
    BackupReminderTooltipStorage backupReminderTooltipStorage;

    @Inject
    UserPreferenceManager userPreferenceManager;

    @Inject
    ReceiptCreateEditFragmentPresenter presenter;

    // Butterknife Fields
    @BindView(R.id.toolbar)
    Toolbar toolbar;

    @BindView(R.id.DIALOG_RECEIPTMENU_NAME)
    AutoCompleteTextView nameBox;

    @BindView(R.id.DIALOG_RECEIPTMENU_PRICE)
    EditText priceBox;

    @BindView(R.id.DIALOG_RECEIPTMENU_TAX)
    AutoCompleteTextView taxBox;

    @BindView(R.id.DIALOG_RECEIPTMENU_CURRENCY)
    Spinner currencySpinner;

    @BindView(R.id.receipt_input_exchange_rate)
    NetworkRequestAwareEditText exchangeRateBox;

    @BindView(R.id.receipt_input_exchanged_result)
    EditText exchangedPriceInBaseCurrencyBox;

    @BindView(R.id.receipt_input_exchange_rate_base_currency)
    TextView receiptInputExchangeRateBaseCurrencyTextView;

    @BindView(R.id.DIALOG_RECEIPTMENU_DATE)
    DateEditText dateBox;

    @BindView(R.id.DIALOG_RECEIPTMENU_CATEGORY)
    Spinner categoriesSpinner;

    @BindView(R.id.DIALOG_RECEIPTMENU_COMMENT)
    AutoCompleteTextView commentBox;

    @BindView(R.id.receipt_input_payment_method)
    Spinner paymentMethodsSpinner;

    @BindView(R.id.DIALOG_RECEIPTMENU_EXPENSABLE)
    CheckBox reimbursableCheckbox;

    @BindView(R.id.DIALOG_RECEIPTMENU_FULLPAGE)
    CheckBox fullpageCheckbox;

    @BindView(R.id.receipt_input_tax_wrapper)
    View taxInputWrapper;

    @BindViews({R.id.receipt_input_guide_image_payment_method, R.id.receipt_input_payment_method})
    List<View> paymentMethodsViewsList;

    @BindViews({R.id.receipt_input_guide_image_exchange_rate, R.id.receipt_input_exchange_rate, R.id.receipt_input_exchanged_result, R.id.receipt_input_exchange_rate_base_currency})
    List<View> exchangeRateViewsList;

    // Flex fields (ie for white-label projects)
    EditText extraEditText1;
    EditText extraEditText2;
    EditText extraEditText3;

    // Misc views
    View focusedView;

    // Butterknife unbinding
    private Unbinder unbinder;

    // Metadata
    private OcrResponse ocrResponse;

    // Presenters
    private CurrencyListEditorPresenter currencyListEditorPresenter;
    private ReceiptPricingPresenter receiptPricingPresenter;
    private CurrencyExchangeRateEditorPresenter currencyExchangeRateEditorPresenter;

    // Rx
    private Disposable idDisposable;
    private TableEventsListener<Category> categoryTableEventsListener;
    private TableEventsListener<PaymentMethod> paymentMethodTableEventsListener;

    // Misc
    private ReceiptInputCache receiptInputCache;
    private AutoCompleteAdapter receiptsNameAutoCompleteAdapter, receiptsCommentAutoCompleteAdapter;
    private List<Category> categoriesList;
    private FooterButtonArrayAdapter<Category> categoriesAdapter;
    private FooterButtonArrayAdapter<PaymentMethod> paymentMethodsAdapter;

    public static ReceiptCreateEditFragment newInstance() {
        return new ReceiptCreateEditFragment();
    }

    @Override
    public void onAttach(Context context) {
        AndroidSupportInjection.inject(this);
        super.onAttach(context);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logger.debug(this, "onCreate");

        ocrResponse = (OcrResponse) getArguments().getSerializable(ARG_OCR);
        receiptInputCache = new ReceiptInputCache(getFragmentManager());
        categoriesList = emptyList();
        categoriesAdapter = new FooterButtonArrayAdapter<>(getActivity(), new ArrayList<Category>(),
                R.string.manage_categories, v -> {
            analytics.record(Events.Informational.ManageCategories);
            navigationHandler.navigateToCategoriesEditor();
        });
        paymentMethodsAdapter = new FooterButtonArrayAdapter<>(getActivity(), new ArrayList<PaymentMethod>(),
                R.string.manage_payment_methods, v -> {
            analytics.record(Events.Informational.ManagePaymentMethods);
            navigationHandler.navigateToPaymentMethodsEditor();
        });

        setHasOptionsMenu(true);

        final DefaultCurrencyListEditorView defaultCurrencyListEditorView = new DefaultCurrencyListEditorView(getContext(), () -> currencySpinner);
        final ReceiptCurrencyCodeSupplier currencyCodeSupplier = new ReceiptCurrencyCodeSupplier(getParentTrip(), receiptInputCache, getReceipt());
        currencyListEditorPresenter = new CurrencyListEditorPresenter(defaultCurrencyListEditorView, database, currencyCodeSupplier, savedInstanceState);
        receiptPricingPresenter = new ReceiptPricingPresenter(this, userPreferenceManager, getReceipt(), savedInstanceState);
        currencyExchangeRateEditorPresenter = new CurrencyExchangeRateEditorPresenter(this, this, defaultCurrencyListEditorView, this, exchangeRateServiceManager, database, getParentTrip(), getReceipt(), savedInstanceState);
    }

    Trip getParentTrip() {
        return getArguments().getParcelable(Trip.PARCEL_KEY);
    }

    Receipt getReceipt() {
        return getArguments().getParcelable(Receipt.PARCEL_KEY);
    }

    File getFile() {
        return (File) getArguments().getSerializable(ARG_FILE);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.update_receipt, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        this.unbinder = ButterKnife.bind(this, view);
        if (savedInstanceState == null) {
            if (getReceipt() == null) {
                new ChildFragmentNavigationHandler(this).addChild(new OcrInformationalTooltipFragment(), R.id.update_receipt_tooltip);
            }
        }

        // Apply white-label settings via our 'Flex' mechanism to update defaults
        flex.applyCustomSettings(nameBox);
        flex.applyCustomSettings(priceBox);
        flex.applyCustomSettings(taxBox);
        flex.applyCustomSettings(currencySpinner);
        flex.applyCustomSettings(exchangeRateBox);
        flex.applyCustomSettings(dateBox);
        flex.applyCustomSettings(categoriesSpinner);
        flex.applyCustomSettings(commentBox);
        flex.applyCustomSettings(reimbursableCheckbox);
        flex.applyCustomSettings(fullpageCheckbox);

        // Apply white-label settings via our 'Flex' mechanism to add custom fields
        final LinearLayout extras = (LinearLayout) flex.getSubView(getActivity(), view, R.id.DIALOG_RECEIPTMENU_EXTRAS);
        this.extraEditText1 = extras.findViewWithTag(getFlexString(R.string.RECEIPTMENU_TAG_EXTRA_EDITTEXT_1));
        this.extraEditText2 = extras.findViewWithTag(getFlexString(R.string.RECEIPTMENU_TAG_EXTRA_EDITTEXT_2));
        this.extraEditText3 = extras.findViewWithTag(getFlexString(R.string.RECEIPTMENU_TAG_EXTRA_EDITTEXT_3));

        // Toolbar stuff
        if (navigationHandler.isDualPane()) {
            toolbar.setVisibility(View.GONE);
        } else {
            setSupportActionBar(toolbar);
        }

        // Set each focus listener, so we can track the focus view across resume -> pauses
        this.nameBox.setOnFocusChangeListener(this);
        this.priceBox.setOnFocusChangeListener(this);
        this.taxBox.setOnFocusChangeListener(this);
        this.currencySpinner.setOnFocusChangeListener(this);
        this.dateBox.setOnFocusChangeListener(this);
        this.commentBox.setOnFocusChangeListener(this);
        this.paymentMethodsSpinner.setOnFocusChangeListener(this);

        // Configure our custom view properties
        exchangeRateBox.setFailedHint(R.string.DIALOG_RECEIPTMENU_HINT_EXCHANGE_RATE_FAILED);

        // And ensure that we do not show the keyboard when clicking these views
        final View.OnTouchListener hideSoftKeyboardOnTouchListener = new SoftKeyboardManager.HideSoftKeyboardOnTouchListener();
        dateBox.setOnTouchListener(hideSoftKeyboardOnTouchListener);
        categoriesSpinner.setOnTouchListener(hideSoftKeyboardOnTouchListener);
        currencySpinner.setOnTouchListener(hideSoftKeyboardOnTouchListener);
        paymentMethodsSpinner.setOnTouchListener(hideSoftKeyboardOnTouchListener);

        // Set-up tax adapter
        if (presenter.isIncludeTaxField()) {
            taxBox.setAdapter(new TaxAutoCompleteAdapter(getActivity(),
                    priceBox,
                    taxBox,
                    presenter.isUsePreTaxPrice(),
                    presenter.getDefaultTaxPercentage(),
                    getReceipt() == null));
        }

        // Outline date defaults
        dateBox.setFocusableInTouchMode(false);
        dateBox.setOnClickListener(dateManager.getDateEditTextListener());
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Configure things if it's not a restored fragment
        if (savedInstanceState == null) {
            if (getReceipt() == null) { // new receipt

                final Time now = new Time();
                now.setToNow();
                if (receiptInputCache.getCachedDate() == null) {
                    if (presenter.isReceiptDateDefaultsToReportStartDate()) {
                        dateBox.date = getParentTrip().getStartDate();
                    } else {
                        dateBox.date = new Date(now.toMillis(false));
                    }
                } else {
                    dateBox.date = receiptInputCache.getCachedDate();
                }
                dateBox.setText(DateFormat.getDateFormat(getActivity()).format(dateBox.date));

                reimbursableCheckbox.setChecked(presenter.isReceiptsDefaultAsReimbursable());
                if (presenter.isMatchReceiptCommentToCategory() && presenter.isMatchReceiptNameToCategory()) {
                    if (focusedView == null) {
                        focusedView = priceBox;
                    }
                } else if (presenter.isMatchReceiptNameToCategory()) {
                    if (focusedView == null) {
                        focusedView = priceBox;
                    }
                }

                fullpageCheckbox.setChecked(presenter.isDefaultToFullPage());

                if (ocrResponse != null) {
                    final OcrResponseParser ocrResponseParser = new OcrResponseParser(ocrResponse);
                    if (ocrResponseParser.getMerchant() != null) {
                        nameBox.setText(ocrResponseParser.getMerchant());
                    }

                    if (presenter.isIncludeTaxField() && ocrResponseParser.getTaxAmount() != null) {
                        taxBox.setText(ocrResponseParser.getTaxAmount());
                        if (ocrResponseParser.getTotalAmount() != null) {
                            if (presenter.isUsePreTaxPrice()) {
                                // If we're in pre-tax mode, let's calculate the price as (total - tax = pre-tax-price)
                                final BigDecimal preTaxPrice = ModelUtils.tryParse(ocrResponseParser.getTotalAmount()).subtract(ModelUtils.tryParse(ocrResponseParser.getTaxAmount()));
                                priceBox.setText(ModelUtils.getDecimalFormattedValue(preTaxPrice));
                            } else {
                                priceBox.setText(ocrResponseParser.getTotalAmount());
                            }
                        }
                    } else if (ocrResponseParser.getTotalAmount() != null) {
                        priceBox.setText(ocrResponseParser.getTotalAmount());
                    }

                    if (ocrResponseParser.getDate() != null) {
                        dateBox.date = ocrResponseParser.getDate();
                        dateBox.setText(DateFormat.getDateFormat(getActivity()).format(dateBox.date));
                    }
                }

            } else { // edit receipt
                final Receipt receipt = getReceipt();
                final Trip parentTrip = getParentTrip();

                nameBox.setText(receipt.getName());
                dateBox.setText(receipt.getFormattedDate(getActivity(), presenter.getDateSeparator()));
                dateBox.date = receipt.getDate();
                commentBox.setText(receipt.getComment());

                reimbursableCheckbox.setChecked(receipt.isReimbursable());
                fullpageCheckbox.setChecked(receipt.isFullPage());

                if (extraEditText1 != null && receipt.hasExtraEditText1()) {
                    extraEditText1.setText(receipt.getExtraEditText1());
                }
                if (extraEditText2 != null && receipt.hasExtraEditText2()) {
                    extraEditText2.setText(receipt.getExtraEditText2());
                }
                if (extraEditText3 != null && receipt.hasExtraEditText3()) {
                    extraEditText3.setText(receipt.getExtraEditText3());
                }
            }

            // Focused View
            if (focusedView == null) {
                focusedView = nameBox;
            }

        }

        // Configure items that require callbacks
        categoryTableEventsListener = new StubTableEventsListener<Category>() {
            @Override
            public void onGetSuccess(@NonNull List<Category> list) {
                if (isAdded()) {
                    categoriesList = list;
                    categoriesAdapter.update(list);
                    categoriesSpinner.setAdapter(categoriesAdapter);

                    if (getReceipt() == null) { // new receipt
                        if (presenter.isPredictCategories()) { // Predict Breakfast, Lunch, Dinner by the hour
                            if (receiptInputCache.getCachedCategory() == null) {
                                final Time now = new Time();
                                now.setToNow();
                                String nameToIndex = null;
                                if (now.hour >= 4 && now.hour < 11) { // Breakfast hours
                                    nameToIndex = getString(R.string.category_breakfast);
                                } else if (now.hour >= 11 && now.hour < 16) { // Lunch hours
                                    nameToIndex = getString(R.string.category_lunch);
                                } else if (now.hour >= 16 && now.hour < 23) { // Dinner hours
                                    nameToIndex = getString(R.string.category_dinner);
                                }
                                if (nameToIndex != null) {
                                    for (int i = 0; i < categoriesAdapter.getCount(); i++) {
                                        final Category category = categoriesAdapter.getItem(i);
                                        if (category != null && nameToIndex.equals(category.getName())) {
                                            categoriesSpinner.setSelection(i);
                                            break; // Exit loop now
                                        }
                                    }
                                }
                            } else {
                                int idx = categoriesAdapter.getPosition(receiptInputCache.getCachedCategory());
                                if (idx > 0) {
                                    categoriesSpinner.setSelection(idx);
                                }
                            }
                        }
                    } else {
                        categoriesSpinner.setSelection(categoriesAdapter.getPosition(getReceipt().getCategory()));
                    }

                    if (presenter.isMatchReceiptCommentToCategory() || presenter.isMatchReceiptNameToCategory()) {
                        categoriesSpinner.setOnItemSelectedListener(new SpinnerSelectionListener());
                    }
                }
            }
        };
        paymentMethodTableEventsListener = new StubTableEventsListener<PaymentMethod>() {
            @Override
            public void onGetSuccess(@NonNull List<PaymentMethod> list) {
                if (isAdded()) {
                    List<PaymentMethod> paymentMethods = new ArrayList<>(list);
                    paymentMethods.add(ImmutablePaymentMethodImpl.NONE);

                    paymentMethodsAdapter.update(paymentMethods);
                    paymentMethodsSpinner.setAdapter(paymentMethodsAdapter);
                    if (presenter.isUsePaymentMethods()) {
                        ButterKnife.apply(paymentMethodsViewsList, ButterKnifeActions.setVisibility(View.VISIBLE));
                        if (getReceipt() != null) {
                            final PaymentMethod oldPaymentMethod = getReceipt().getPaymentMethod();
                            final int paymentIdx = paymentMethodsAdapter.getPosition(oldPaymentMethod);
                            if (paymentIdx > 0) {
                                paymentMethodsSpinner.setSelection(paymentIdx);
                            }
                        }
                    } else {
                        ButterKnife.apply(paymentMethodsViewsList, ButterKnifeActions.setVisibility(View.GONE));
                    }
                }
            }
        };
        categoriesTableController.subscribe(categoryTableEventsListener);
        paymentMethodsTableController.subscribe(paymentMethodTableEventsListener);
        categoriesTableController.get();
        paymentMethodsTableController.get();
    }

    @Override
    public void onResume() {
        super.onResume();
        Logger.debug(this, "onResume");

        final boolean isNewReceipt = getReceipt() == null;

        final String title;
        if (isNewReceipt) {
            title = getFlexString(R.string.DIALOG_RECEIPTMENU_TITLE_NEW);
        } else {
            if (presenter.isShowReceiptId()) {
                title = String.format(getFlexString(R.string.DIALOG_RECEIPTMENU_TITLE_EDIT_ID), getReceipt().getId());
            } else {
                title = getFlexString(R.string.DIALOG_RECEIPTMENU_TITLE_EDIT);
            }
        }

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeAsUpIndicator(R.drawable.ic_action_cancel);
            actionBar.setTitle(title);
            actionBar.setSubtitle("");
        }

        if (isNewReceipt && presenter.isShowReceiptId()) {
            idDisposable = database.getNextReceiptAutoIncremenetIdHelper()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(receiptId -> {
                        if (isResumed()) {
                            final ActionBar bar = getSupportActionBar();
                            if (bar != null) {
                                final String titleWithId = String.format(getFlexString(R.string.DIALOG_RECEIPTMENU_TITLE_NEW_ID), receiptId);
                                bar.setTitle(titleWithId);
                            }
                        }
                    });
        }

        if (isNewReceipt) {
            if (presenter.isEnableAutoCompleteSuggestions()) {
                if (receiptsNameAutoCompleteAdapter == null) {
                    receiptsNameAutoCompleteAdapter = AutoCompleteAdapter.getInstance(getActivity(),
                            DatabaseHelper.TAG_RECEIPTS_NAME, database, database);
                } else {
                    receiptsNameAutoCompleteAdapter.reset();
                }
                if (receiptsCommentAutoCompleteAdapter == null) {
                    receiptsCommentAutoCompleteAdapter = AutoCompleteAdapter.getInstance(getActivity(),
                            DatabaseHelper.TAG_RECEIPTS_COMMENT, database);
                } else {
                    receiptsCommentAutoCompleteAdapter.reset();
                }
                nameBox.setAdapter(receiptsNameAutoCompleteAdapter);
                commentBox.setAdapter(receiptsCommentAutoCompleteAdapter);
            }
        }

        if (focusedView != null) {
            focusedView.requestFocus(); // Make sure we're focused on the right view
        }

        database.registerReceiptAutoCompleteListener(this);

        // Presenters
        currencyListEditorPresenter.subscribe();
        receiptPricingPresenter.subscribe();
        currencyExchangeRateEditorPresenter.subscribe();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_save, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            navigationHandler.navigateBack();
            presenter.deleteReceiptFileIfUnused();
            return true;
        }
        if (item.getItemId() == R.id.action_save) {
            saveReceipt();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        focusedView = hasFocus ? v : null;
        if (getReceipt() == null && hasFocus) {
            // Only launch if we have focus and it's a new receipt
            SoftKeyboardManager.showKeyboard(v);
        }
    }

    @Override
    public void onReceiptRowAutoCompleteQueryResult(String name, String price, @Nullable Integer categoryId) {
        if (isAdded()) {
            if (nameBox != null && name != null) {
                nameBox.setText(name);
                nameBox.setSelection(name.length());
            }
            if (priceBox != null && price != null && priceBox.getText().length() == 0) {
                priceBox.setText(price);
            }

            if (categoriesSpinner != null && categoryId != null) {
                for (int i = 0; i < categoriesList.size(); i++) {
                    if (categoryId == categoriesList.get(i).getId()) {
                        categoriesSpinner.setSelection(categoriesList.indexOf(categoriesList.get(i)));
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void onPause() {
        // Presenters
        receiptPricingPresenter.unsubscribe();
        currencyListEditorPresenter.unsubscribe();
        currencyExchangeRateEditorPresenter.unsubscribe();

        // Notify the downstream adapters
        if (receiptsNameAutoCompleteAdapter != null) {
            receiptsNameAutoCompleteAdapter.onPause();
        }
        if (receiptsCommentAutoCompleteAdapter != null) {
            receiptsCommentAutoCompleteAdapter.onPause();
        }
        if (idDisposable != null) {
            idDisposable.dispose();
            idDisposable = null;
        }

        // Dismiss the soft keyboard
        SoftKeyboardManager.hideKeyboard(focusedView);

        database.unregisterReceiptAutoCompleteListener();
        super.onPause();
    }


    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Logger.debug(this, "onSaveInstanceState");

        // Presenters
        currencyListEditorPresenter.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        Logger.debug(this, "onDestroyView");
        extraEditText1 = null;
        extraEditText2 = null;
        extraEditText3 = null;
        focusedView = null;
        unbinder.unbind();
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        categoriesTableController.unsubscribe(categoryTableEventsListener);
        paymentMethodsTableController.unsubscribe(paymentMethodTableEventsListener);
        super.onDestroy();
    }

    private void saveReceipt() {

        if (presenter.checkReceipt(dateBox.date)) {
            final String name = TextUtils.isEmpty(nameBox.getText().toString()) ? "" : nameBox.getText().toString();
            final Category category = categoriesAdapter.getItem(categoriesSpinner.getSelectedItemPosition());
            final String currency = currencySpinner.getSelectedItem().toString();
            final String price = priceBox.getText().toString();
            final String tax = taxBox.getText().toString();
            final String exchangeRate = exchangeRateBox.getText().toString();
            final String comment = commentBox.getText().toString();
            final PaymentMethod paymentMethod = (PaymentMethod) (presenter.isUsePaymentMethods() ? paymentMethodsSpinner.getSelectedItem() : null);
            final String extraText1 = (extraEditText1 == null) ? null : extraEditText1.getText().toString();
            final String extraText2 = (extraEditText2 == null) ? null : extraEditText2.getText().toString();
            final String extraText3 = (extraEditText3 == null) ? null : extraEditText3.getText().toString();

            final Date receiptDate;

            // updating date just if it was really changed (to prevent reordering)
            if (getReceipt() != null && getReceipt().getDate().equals(dateBox.date)) {
                receiptDate = getReceipt().getDate();
            } else {
                Calendar cal = Calendar.getInstance();
                long secondsOfDay = TimeUnit.HOURS.toSeconds(cal.get(Calendar.HOUR_OF_DAY)) +
                        TimeUnit.MINUTES.toSeconds(cal.get(Calendar.MINUTE)) +
                        cal.get(Calendar.SECOND);
                cal.setTime(dateBox.date);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);

                // Note: we're saving date that was picked by user (without time information) + current secondsOfDay
                receiptDate = new Date(cal.getTimeInMillis() + secondsOfDay);
            }

            receiptInputCache.setCachedDate((Date) dateBox.date.clone());
            receiptInputCache.setCachedCategory(category);
            receiptInputCache.setCachedCurrency(currency);

            presenter.saveReceipt(receiptDate, price, tax, exchangeRate, comment,
                    paymentMethod, reimbursableCheckbox.isChecked(), fullpageCheckbox.isChecked(), name, category, currency,
                    extraText1, extraText2, extraText3);

            analytics.record(getReceipt() == null ? Events.Receipts.PersistNewReceipt : Events.Receipts.PersistUpdateReceipt);
            dateManager.setDateEditTextListenerDialogHolder(null);

            backupReminderTooltipStorage.setOneMoreNewReceipt();

            navigationHandler.navigateBack();
        }
    }

    public void showDateError() {
        Toast.makeText(getActivity(), getFlexString(R.string.CALENDAR_TAB_ERROR), Toast.LENGTH_SHORT).show();
    }

    public void showDateWarning() {
        Toast.makeText(getActivity(), getFlexString(R.string.DIALOG_RECEIPTMENU_TOAST_BAD_DATE), Toast.LENGTH_LONG).show();
    }

    @NonNull
    @UiThread
    @Override
    public Consumer<? super Price> displayReceiptPrice() {
        return RxTextViewExtensions.price(priceBox);
    }

    @NonNull
    @UiThread
    @Override
    public Consumer<? super Price> displayReceiptTax() {
        return RxTextViewExtensions.price(taxBox);
    }

    @NonNull
    @UiThread
    @Override
    public Consumer<? super Boolean> toggleReceiptTaxFieldVisibility() {
        return RxView.visibility(taxInputWrapper);
    }

    @NonNull
    @UiThread
    @Override
    public Observable<CharSequence> getReceiptPriceChanges() {
        return RxTextView.textChanges(priceBox);
    }

    @NonNull
    @UiThread
    @Override
    public Observable<CharSequence> getReceiptTaxChanges() {
        return RxTextView.textChanges(taxBox);
    }

    @NonNull
    @UiThread
    @Override
    public Observable<Date> getReceiptDateChanges() {
        return RxDateEditText.dateChanges(dateBox)
                .doOnNext(ignored -> {
                    if (exchangedPriceInBaseCurrencyBox.isFocused()) {
                        exchangedPriceInBaseCurrencyBox.clearFocus();
                    }
                });
    }

    @NonNull
    @UiThread
    @Override
    public Consumer<? super Boolean> toggleExchangeRateFieldVisibility() {
        return (Consumer<Boolean>) isVisible -> {
            ButterKnife.apply(exchangeRateViewsList, ButterKnifeActions.setVisibility(isVisible ? View.VISIBLE : View.GONE));
            if (!isVisible) {
                // Clear out if we're hiding the box
                exchangeRateBox.setText("");
            }
        };
    }

    @NonNull
    @UiThread
    @Override
    public Consumer<? super UiIndicator<ExchangeRate>> displayExchangeRate() {
        return (Consumer<UiIndicator<ExchangeRate>>) exchangeRateUiIndicator -> {
            if (exchangeRateUiIndicator.getState() == UiIndicator.State.Loading) {
                exchangeRateBox.setText("");
                exchangeRateBox.setCurrentState(NetworkRequestAwareEditText.State.Loading);
            } else if (exchangeRateUiIndicator.getState() == UiIndicator.State.Error) {
                exchangeRateBox.setCurrentState(NetworkRequestAwareEditText.State.Failure);
            } else if (exchangeRateUiIndicator.getState() == UiIndicator.State.Success) {
                if (exchangeRateUiIndicator.getData().isPresent()) {
                    if (TextUtils.isEmpty(exchangeRateBox.getText()) || exchangedPriceInBaseCurrencyBox.isFocused()) {
                        exchangeRateBox.setText(exchangeRateUiIndicator.getData().get().getDecimalFormattedExchangeRate(getParentTrip().getDefaultCurrencyCode()));
                    } else {
                        Logger.warn(ReceiptCreateEditFragment.this, "Ignoring remote exchange rate result now that one is already set");
                    }
                    exchangeRateBox.setCurrentState(NetworkRequestAwareEditText.State.Success);
                } else {
                    // If the data is empty, reset to use the ready state to allow for user interaction
                    exchangeRateBox.setText("");
                }
            } else {
                exchangeRateBox.setCurrentState(NetworkRequestAwareEditText.State.Ready);
            }
        };
    }

    @NonNull
    @UiThread
    @Override
    public Consumer<? super PriceCurrency> displayBaseCurrency() {
        return (Consumer<PriceCurrency>) priceCurrency -> receiptInputExchangeRateBaseCurrencyTextView.setText(priceCurrency.getCurrencyCode());
    }

    @NonNull
    @UiThread
    @Override
    public Consumer<? super Optional<Price>> displayExchangedPriceInBaseCurrency() {
        return RxTextViewExtensions.priceOptional(exchangedPriceInBaseCurrencyBox);
    }

    @NonNull
    @UiThread
    @Override
    public Observable<CharSequence> getExchangeRateChanges() {
        return RxTextView.textChanges(exchangeRateBox);
    }

    @NonNull
    @UiThread
    @Override
    public Observable<CharSequence> getExchangedPriceInBaseCurrencyChanges() {
        return RxTextView.textChanges(exchangedPriceInBaseCurrencyBox);
    }

    @NonNull
    @Override
    public Observable<Boolean> getExchangedPriceInBaseCurrencyFocusChanges() {
        return RxView.focusChanges(exchangedPriceInBaseCurrencyBox);
    }

    @NonNull
    @UiThread
    @Override
    public Observable<Object> getUserInitiatedExchangeRateRetries() {
        return exchangeRateBox.getUserRetries()
                .doOnNext(ignored -> {
                    if (exchangedPriceInBaseCurrencyBox.isFocused()) {
                        exchangedPriceInBaseCurrencyBox.clearFocus();
                    }
                });
    }

    private class SpinnerSelectionListener implements AdapterView.OnItemSelectedListener {

        @Override
        public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
            if (presenter.isMatchReceiptNameToCategory()) {
                nameBox.setText(categoriesAdapter.getItem(position).getName());
            }
            if (presenter.isMatchReceiptCommentToCategory()) {
                commentBox.setText(categoriesAdapter.getItem(position).getName());
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> arg0) {
        }
    }

    private String getFlexString(int id) {
        return getFlexString(flex, id);
    }

}

package co.smartreceipts.android.persistence;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hadisatrio.optional.Optional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.annotation.Nonnull;

import co.smartreceipts.analytics.log.Logger;
import co.smartreceipts.android.database.DatabaseContext;
import co.smartreceipts.android.date.DateUtils;
import co.smartreceipts.android.model.Distance;
import co.smartreceipts.android.model.Priceable;
import co.smartreceipts.android.model.Receipt;
import co.smartreceipts.android.model.Trip;
import co.smartreceipts.android.model.factory.PriceBuilderFactory;
import co.smartreceipts.android.model.impl.columns.receipts.ReceiptColumnDefinitions;
import co.smartreceipts.android.model.utils.CurrencyUtils;
import co.smartreceipts.android.model.utils.CurrencyWithDecimalPlaces;
import co.smartreceipts.android.persistence.database.defaults.TableDefaultsCustomizer;
import co.smartreceipts.android.persistence.database.tables.CSVTable;
import co.smartreceipts.android.persistence.database.tables.CategoriesTable;
import co.smartreceipts.android.persistence.database.tables.DistanceTable;
import co.smartreceipts.android.persistence.database.tables.PDFTable;
import co.smartreceipts.android.persistence.database.tables.PaymentMethodsTable;
import co.smartreceipts.android.persistence.database.tables.ReceiptsTable;
import co.smartreceipts.android.persistence.database.tables.Table;
import co.smartreceipts.android.persistence.database.tables.TripsTable;
import co.smartreceipts.android.persistence.database.tables.ordering.OrderingPreferencesManager;
import co.smartreceipts.android.settings.UserPreferenceManager;
import co.smartreceipts.android.settings.catalog.UserPreference;
import co.smartreceipts.android.utils.sorting.AlphabeticalCaseInsensitiveCharSequenceComparator;
import co.smartreceipts.core.di.scopes.ApplicationScope;
import co.smartreceipts.core.persistence.DatabaseConstants;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import wb.android.storage.StorageManager;

@ApplicationScope
public class DatabaseHelper extends SQLiteOpenHelper {

    // Database Info
    public static final int DATABASE_VERSION = 21;

    @Deprecated
    public static final String NO_DATA = "null"; // TODO: Just set to null

    // InstanceVar
    private static DatabaseHelper INSTANCE = null;

    private static final Executor DATABASE_CLOSE_EXECUTOR = Executors.newSingleThreadExecutor();


    // Caching Vars
    private ArrayList<CharSequence> mFullCurrencyList;
    private ArrayList<CharSequence> mMostRecentlyUsedCurrencyList;

    // Other vars
    private final DatabaseContext mContext;
    private final TableDefaultsCustomizer mCustomizations;
    private final UserPreferenceManager mPreferences;

    // Locks
    private final Object mDatabaseLock = new Object();

    // Tables
    private final List<Table> mTables;
    private final TripsTable mTripsTable;
    private final ReceiptsTable mReceiptsTable;
    private final DistanceTable mDistanceTable;
    private final CategoriesTable mCategoriesTable;
    private final CSVTable mCSVTable;
    private final PDFTable mPDFTable;
    private final PaymentMethodsTable mPaymentMethodsTable;

    private int databaseStartingVersion = DATABASE_VERSION;

    // Misc Vars
    private boolean mIsDBOpen = false;

    public interface ReceiptAutoCompleteListener {

        void onReceiptRowAutoCompleteQueryResult(@Nullable String name, @Nullable String price, @Nullable Integer categoryId);
    }

    public DatabaseHelper(@NonNull DatabaseContext context,
                          @NonNull StorageManager storageManager,
                          @NonNull UserPreferenceManager preferences,
                          @NonNull ReceiptColumnDefinitions receiptColumnDefinitions,
                          @NonNull TableDefaultsCustomizer tableDefaultsCustomizer,
                          @NonNull OrderingPreferencesManager orderingPreferencesManager,
                          @NonNull Optional<String> databasePathOptional) {
        super(context, databasePathOptional.or(DatabaseConstants.DATABASE_NAME), null, DATABASE_VERSION); // Requests the default cursor

        mContext = context;
        mPreferences = preferences;
        mCustomizations = tableDefaultsCustomizer;

        // Tables:
        mTables = new ArrayList<>();
        mTripsTable = new TripsTable(this, storageManager, preferences);
        mCategoriesTable = new CategoriesTable(this, orderingPreferencesManager);
        mCSVTable = new CSVTable(this, receiptColumnDefinitions, orderingPreferencesManager);
        mPDFTable = new PDFTable(this, receiptColumnDefinitions, orderingPreferencesManager);
        mPaymentMethodsTable = new PaymentMethodsTable(this, orderingPreferencesManager);
        mDistanceTable = new DistanceTable(this, mTripsTable, mPaymentMethodsTable, preferences);
        mReceiptsTable = new ReceiptsTable(this, mTripsTable, mPaymentMethodsTable, mCategoriesTable, storageManager, preferences, orderingPreferencesManager);
        mTables.add(mTripsTable);
        mTables.add(mDistanceTable);
        mTables.add(mCategoriesTable);
        mTables.add(mCSVTable);
        mTables.add(mPDFTable);
        mTables.add(mPaymentMethodsTable);
        mTables.add(mReceiptsTable);

    }

    @NonNull
    public static synchronized DatabaseHelper getInstance(@NonNull DatabaseContext context,
                                                          @NonNull StorageManager storageManager,
                                                          @NonNull UserPreferenceManager preferences,
                                                          @NonNull ReceiptColumnDefinitions receiptColumnDefinitions,
                                                          @NonNull TableDefaultsCustomizer tableDefaultsCustomizer,
                                                          @NonNull OrderingPreferencesManager orderingPreferencesManager) {
        // If we don't have an instance or it's closed
        if (INSTANCE == null || !INSTANCE.isOpen()) {
            INSTANCE = new DatabaseHelper(context, storageManager, preferences, receiptColumnDefinitions, tableDefaultsCustomizer, orderingPreferencesManager, Optional.absent());
        }
        return INSTANCE;
    }


    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // Begin Abstract Method Overrides
    // //////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onCreate(final SQLiteDatabase db) {
        Logger.info(this, "onCreate");
        Logger.info(this, "Clearing out our clear-able preferences to avoid any syncing issues due if our data was only partially wiped");
        SharedPreferenceDefinitions.clearPreferencesThatCanBeCleared(mContext);
        for (final Table table : mTables) {
            table.onCreate(db, mCustomizations);
        }

        for (final Table table : mTables) {
            table.onPostCreateUpgrade();
        }

    }

    @Override
    public void onUpgrade(final SQLiteDatabase db, int oldVersion, final int newVersion) {
        Logger.info(this, "onUpgrade from {} to {}.", oldVersion, newVersion);
        this.databaseStartingVersion = oldVersion;

        for (final Table table : mTables) {
            table.onUpgrade(db, oldVersion, newVersion, mCustomizations);
        }

        for (final Table table : mTables) {
            table.onPostCreateUpgrade();
        }
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        try {
            // We disable WAL to simplify our backup structure
            db.disableWriteAheadLogging();
        } catch (IllegalStateException e) {
            Logger.error(this, "Failed to disable WAL");
        }
        mIsDBOpen = true;
    }

    @Override
    public synchronized void close() {
        super.close();
        mIsDBOpen = false;
    }

    public boolean isOpen() {
        return mIsDBOpen;
    }

    public void onDestroy() {
        DATABASE_CLOSE_EXECUTOR.execute(() -> {
            try {
                this.close();
            } catch (Exception e) {
                // This can be called from finalize, so operate cautiously
                Logger.error(this, e);
            }
        });
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // Utility Methods
    // //////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * This class is not synchronized! Sync outside of it
     *
     * @param trip The trip we want the prices of
     */
    public void getTripPriceAndDailyPriceAndSize(final Trip trip) {
        queryTripPrice(trip);
        queryTripDailyPrice(trip);
        queryTripSize(trip);
    }

    /**
     * Queries the trips price and updates this object. This class is not synchronized! Sync outside of it
     *
     * @param trip the trip, which will be updated
     */
    private void queryTripPrice(final Trip trip) {
        final boolean onlyUseReimbursable = mPreferences.get(UserPreference.Receipts.OnlyIncludeReimbursable);
        final List<Receipt> receipts = mReceiptsTable.getBlocking(trip, true);
        final List<Priceable> prices = new ArrayList<>(receipts.size());
        for (final Receipt receipt : receipts) {
            if (!onlyUseReimbursable || receipt.isReimbursable()) {
                prices.add(receipt);
            }
        }

        if (mPreferences.get(UserPreference.Distance.IncludeDistancePriceInReports)) {
            final List<Distance> distances = mDistanceTable.getBlocking(trip, true);
            prices.addAll(distances);
        }

        trip.setPrice(new PriceBuilderFactory().setPriceables(prices, trip.getTripCurrency()).build());
    }

    /**
     * Queries the trips daily total price and updates this object. This class is not synchronized! Sync outside of it
     *
     * @param trip the trip, which will be updated
     */
    private void queryTripDailyPrice(final Trip trip) {
        final boolean onlyUseReimbursable = mPreferences.get(UserPreference.Receipts.OnlyIncludeReimbursable);
        final List<Receipt> receipts = mReceiptsTable.getBlocking(trip, true);
        final List<Priceable> prices = new ArrayList<>(receipts.size());
        for (final Receipt receipt : receipts) {
            if (!onlyUseReimbursable || receipt.isReimbursable()) {
                if (DateUtils.isToday(receipt.getDate())) {
                    prices.add(receipt);
                }
            }
        }

        if (mPreferences.get(UserPreference.Distance.IncludeDistancePriceInReports)) {
            final List<Distance> distances = mDistanceTable.getBlocking(trip, true);
            for (final Distance distance : distances) {
                if (DateUtils.isToday(distance.getDate())) {
                    prices.add(distance);
                }
            }
        }

        trip.setDailySubTotal(new PriceBuilderFactory().setPriceables(prices, trip.getTripCurrency()).build());
    }

    private void queryTripSize(final Trip trip) {
        final List<Receipt> receipts = mReceiptsTable.getBlocking(trip, true);
        trip.setSize(receipts.size());
    }

    public Single<Integer> getNextReceiptAutoIncrementIdHelper() {
        return Single.fromCallable(() -> {
            SQLiteDatabase db = getReadableDatabase();

            try (Cursor cursor = db.rawQuery("SELECT seq FROM SQLITE_SEQUENCE WHERE name=?", new String[]{ReceiptsTable.TABLE_NAME})) {
                if (cursor != null && cursor.moveToFirst() && cursor.getColumnCount() > 0) {
                    return cursor.getInt(0) + 1;
                } else {
                    return 0;
                }
            }
        });
    }

    public List<CharSequence> getCurrenciesList() {
        if (mFullCurrencyList != null) {
            return mFullCurrencyList;
        }

        mFullCurrencyList = new ArrayList<>();

        for (CurrencyWithDecimalPlaces currency : CurrencyUtils.INSTANCE.getCurrencies()) {
            mFullCurrencyList.add(currency.getCurrencyCode());
        }
        mFullCurrencyList.addAll(0, getMostRecentlyUsedCurrencies());
        return mFullCurrencyList;
    }

    public Single<List<String>> search(@NonNull String input, @Nonnull String tableName, @Nonnull String resultColumn,
                                       @Nullable String orderByColumn, @Nonnull String... searchColumns) {
        return Single.fromCallable(() -> {
                    final List<String> results = new ArrayList<>();

                    synchronized (mDatabaseLock) {
                        Cursor cursor = null;

                        try {
                            final SQLiteDatabase db = getReadableDatabase();
                            final String baseQuery = String.format("SELECT DISTINCT %s FROM %s WHERE ", resultColumn, tableName);
                            StringBuilder builder = new StringBuilder(baseQuery);

                            for (int i = 0; i < searchColumns.length; i++) {
                                if (i != 0) {
                                    builder.append(" OR ");
                                }

                                if (searchColumns[i].equals(ReceiptsTable.COLUMN_COMMENT)) {
                                    builder.append(searchColumns[i])
                                            .append(" like '%")
                                            .append(input)
                                            .append("%' ");
                                } else {
                                    builder.append(searchColumns[i])
                                            .append(" like '")
                                            .append(input)
                                            .append("%' ");
                                }
                            }

                            if (orderByColumn != null) {
                                builder.append(" ORDER BY ")
                                        .append(orderByColumn);
                            }

                            cursor = db.rawQuery(builder.toString(), null);
                            if (cursor != null && cursor.moveToFirst()) {
                                do {
                                    results.add(cursor.getString(0));
                                } while (cursor.moveToNext());
                            }

                        } finally {
                            if (cursor != null) {
                                cursor.close();
                            }
                        }
                    }
                    return results;
                }
        )
                .subscribeOn(Schedulers.io());
    }

    private List<CharSequence> getMostRecentlyUsedCurrencies() {
        if (mMostRecentlyUsedCurrencyList != null) {
            return mMostRecentlyUsedCurrencyList;
        }
        mMostRecentlyUsedCurrencyList = new ArrayList<>();
        final String query = "SELECT " + ReceiptsTable.COLUMN_ISO4217 + ", COUNT(*) FROM " + ReceiptsTable.TABLE_NAME + " GROUP BY " + ReceiptsTable.COLUMN_ISO4217;
        synchronized (mDatabaseLock) {
            Cursor cursor = null;
            try {
                final SQLiteDatabase db = this.getReadableDatabase();
                cursor = db.rawQuery(query, new String[0]);
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        mMostRecentlyUsedCurrencyList.add(cursor.getString(0));
                    }
                    while (cursor.moveToNext());
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        Collections.sort(mMostRecentlyUsedCurrencyList, new AlphabeticalCaseInsensitiveCharSequenceComparator());
        return mMostRecentlyUsedCurrencyList;
    }

    /**
     * @return the original version of this database before being attached. This is only useful in
     * the event that our {@link #onUpgrade(SQLiteDatabase, int, int)} script was triggered as it
     * will always be equal to the {@link #DATABASE_VERSION} otherwise.
     */
    public final int getDatabaseStartingVersion() {
        return this.databaseStartingVersion;
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // Tables Methods
    // //////////////////////////////////////////////////////////////////////////////////////////////////
    @NonNull
    public final TripsTable getTripsTable() {
        return mTripsTable;
    }

    @NonNull
    public final ReceiptsTable getReceiptsTable() {
        return mReceiptsTable;
    }

    @NonNull
    public final DistanceTable getDistanceTable() {
        return mDistanceTable;
    }

    @NonNull
    public final CategoriesTable getCategoriesTable() {
        return mCategoriesTable;
    }

    @NonNull
    public final CSVTable getCSVTable() {
        return mCSVTable;
    }

    @NonNull
    public final PDFTable getPDFTable() {
        return mPDFTable;
    }

    @NonNull
    public final PaymentMethodsTable getPaymentMethodsTable() {
        return mPaymentMethodsTable;
    }

    @NonNull
    public final List<Table> getTables() {
        return mTables;
    }

}

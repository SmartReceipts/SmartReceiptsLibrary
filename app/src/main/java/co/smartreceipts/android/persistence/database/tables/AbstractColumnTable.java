package co.smartreceipts.android.persistence.database.tables;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.NonNull;

import com.google.common.base.Preconditions;

import co.smartreceipts.android.model.Column;
import co.smartreceipts.android.model.ColumnDefinitions;
import co.smartreceipts.android.model.ColumnFinder;
import co.smartreceipts.android.model.Receipt;
import co.smartreceipts.android.persistence.database.defaults.TableDefaultsCustomizer;
import co.smartreceipts.android.persistence.database.tables.adapters.ColumnDatabaseAdapter;
import co.smartreceipts.android.persistence.database.tables.keys.ColumnPrimaryKey;
import co.smartreceipts.android.persistence.database.tables.ordering.OrderByColumn;
import co.smartreceipts.android.persistence.database.tables.ordering.OrderByDatabaseDefault;
import co.smartreceipts.android.persistence.database.tables.ordering.OrderByOrderingPreference;
import co.smartreceipts.android.persistence.database.tables.ordering.OrderingPreferencesManager;
import co.smartreceipts.android.utils.log.Logger;

/**
 * Since our CSV and PDF tables share almost all of the same logic, this class purely acts as a wrapper around
 * each to centralize where all logic is managed
 */
public abstract class AbstractColumnTable extends AbstractSqlTable<Column<Receipt>, Integer> {

    public static final String COLUMN_ID = "id";
    public static final String COLUMN_TYPE = "column_type";

    private final int tableExistsSinceDatabaseVersion;
    private final ColumnDefinitions<Receipt> receiptColumnDefinitions;

    @Deprecated
    public static final String idColumnName = "id";
    @Deprecated
    public static final String typeColumnName = "type";


    public AbstractColumnTable(@NonNull SQLiteOpenHelper sqLiteOpenHelper,
                               @NonNull String tableName,
                               int tableExistsSinceDatabaseVersion,
                               @NonNull ColumnDefinitions<Receipt> columnDefinitions,
                               @NonNull OrderingPreferencesManager orderingPreferencesManager,
                               @NonNull Class<? extends Table<?, ?>> tableClass) {
        super(sqLiteOpenHelper, tableName, new ColumnDatabaseAdapter(columnDefinitions),
                new ColumnPrimaryKey(COLUMN_ID), new OrderByOrderingPreference(orderingPreferencesManager, tableClass, new OrderByColumn(COLUMN_CUSTOM_ORDER_ID, false), new OrderByDatabaseDefault()));
        this.tableExistsSinceDatabaseVersion = tableExistsSinceDatabaseVersion;
        receiptColumnDefinitions = Preconditions.checkNotNull(columnDefinitions);
    }

    @Override
    public synchronized void onCreate(@NonNull SQLiteDatabase db, @NonNull TableDefaultsCustomizer customizer) {
        super.onCreate(db, customizer);
        final String columnsTable = "CREATE TABLE " + getTableName() + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_TYPE + " INTEGER DEFAULT 0, "
                + AbstractSqlTable.COLUMN_DRIVE_SYNC_ID + " TEXT, "
                + AbstractSqlTable.COLUMN_DRIVE_IS_SYNCED + " BOOLEAN DEFAULT 0, "
                + AbstractSqlTable.COLUMN_DRIVE_MARKED_FOR_DELETION + " BOOLEAN DEFAULT 0, "
                + AbstractSqlTable.COLUMN_LAST_LOCAL_MODIFICATION_TIME + " DATE, "
                + AbstractSqlTable.COLUMN_CUSTOM_ORDER_ID + " INTEGER DEFAULT 0"
                + ");";
        Logger.debug(this, columnsTable);

        db.execSQL(columnsTable);
        insertDefaults(customizer);
    }

    @Override
    public synchronized void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion, @NonNull TableDefaultsCustomizer customizer) {
        super.onUpgrade(db, oldVersion, newVersion, customizer);
        if (oldVersion <= tableExistsSinceDatabaseVersion) {
            final String columnsTable = "CREATE TABLE " + getTableName() + " ("
                    + idColumnName + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + typeColumnName + " TEXT"
                    + ");";
            Logger.debug(this, columnsTable);

            db.execSQL(columnsTable);
            insertDefaults(customizer);
        }
        if (oldVersion <= 14) {
            onUpgradeToAddSyncInformation(db, oldVersion, newVersion);
        }
        if (oldVersion <= 15) { // adding custom_order_id column
            final String addCustomOrderColumn = String.format("ALTER TABLE %s ADD COLUMN %s INTEGER DEFAULT 0;",
                    getTableName(), AbstractColumnTable.COLUMN_CUSTOM_ORDER_ID);
            Logger.debug(this, addCustomOrderColumn);
            db.execSQL(addCustomOrderColumn);
        }
        if (oldVersion <= 17) { // removing unused typeColumnName column, adding COLUMN_TYPE column
            // adding new column column_type
            final String addNewColumn = String.format("ALTER TABLE %s ADD COLUMN %s INTEGER DEFAULT 0;",
                    getTableName(), AbstractColumnTable.COLUMN_TYPE);
            Logger.debug(this, addNewColumn);
            db.execSQL(addNewColumn);

            // finding new column types for old values (adding default column if correct type wasn't found)
            Cursor columnsCursor = null;
            try {
                columnsCursor = db.query(getTableName(), new String[]{idColumnName, typeColumnName, COLUMN_TYPE}, null, null, null, null, null);
                if (columnsCursor != null && columnsCursor.moveToFirst()) {
                    final int idIndex = columnsCursor.getColumnIndex(idColumnName);
                    final int typeIndex = columnsCursor.getColumnIndex(typeColumnName);

                    do {
                        final int id = columnsCursor.getInt(idIndex);
                        final String oldColumnType = columnsCursor.getString(typeIndex);

                        int newColumnType = receiptColumnDefinitions.getDefaultInsertColumn().getType();

                        if (receiptColumnDefinitions instanceof ColumnFinder) {
                            final int columnTypeByHeaderValue = ((ColumnFinder) receiptColumnDefinitions).getColumnTypeByHeaderValue(oldColumnType);
                            if (columnTypeByHeaderValue >= 0) {
                                newColumnType = columnTypeByHeaderValue;
                            }
                        }

                        final ContentValues columnValues = new ContentValues(1);
                        columnValues.put(COLUMN_TYPE, newColumnType);
                        Logger.debug(this, "Updating old column header value: {} to new column type {}", oldColumnType, newColumnType);

                        if (db.update(getTableName(), columnValues, COLUMN_ID + "= ?", new String[]{Integer.toString(id)}) == 0) {
                            Logger.error(this, "Column update error happened");
                        }


                    } while (columnsCursor.moveToNext());
                }
            } finally {
                if (columnsCursor != null) {
                    columnsCursor.close();
                }
            }

            // removing old column type column (rename old table, create new correct table, insert data, drop old table)
            final String baseColumns = String.format("%s, %s, %s, %s, %s, %s", COLUMN_TYPE, AbstractSqlTable.COLUMN_DRIVE_SYNC_ID,
                    AbstractSqlTable.COLUMN_DRIVE_IS_SYNCED, AbstractSqlTable.COLUMN_DRIVE_MARKED_FOR_DELETION,
                    AbstractSqlTable.COLUMN_LAST_LOCAL_MODIFICATION_TIME,
                    AbstractSqlTable.COLUMN_CUSTOM_ORDER_ID);

            final String renameTable = String.format("ALTER TABLE %s RENAME TO %s;", getTableName(), getTableName() + "_tmp");
            Logger.debug(this, renameTable);
            db.execSQL(renameTable);

            final String createNewTable = "CREATE TABLE " + getTableName() + " ("
                    + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COLUMN_TYPE + " INTEGER DEFAULT 0, "
                    + AbstractSqlTable.COLUMN_DRIVE_SYNC_ID + " TEXT, "
                    + AbstractSqlTable.COLUMN_DRIVE_IS_SYNCED + " BOOLEAN DEFAULT 0, "
                    + AbstractSqlTable.COLUMN_DRIVE_MARKED_FOR_DELETION + " BOOLEAN DEFAULT 0, "
                    + AbstractSqlTable.COLUMN_LAST_LOCAL_MODIFICATION_TIME + " DATE, "
                    + AbstractSqlTable.COLUMN_CUSTOM_ORDER_ID + " INTEGER DEFAULT 0"
                    + ");";
            Logger.debug(this, createNewTable);
            db.execSQL(createNewTable);

            final String insertData = "INSERT INTO " + getTableName()
                    + " (" + COLUMN_ID + ", " + baseColumns + ") "
                    + "SELECT " + idColumnName + ", " + baseColumns
                    + " FROM " + getTableName() + "_tmp"+ ";";
            Logger.debug(this, insertData);
            db.execSQL(insertData);

            final String dropOldTable = "DROP TABLE " + getTableName() + "_tmp" + ";";
            Logger.debug(this, dropOldTable);
            db.execSQL(dropOldTable);
        }
    }

    /**
     * Passes alongs a call to insert our "table" defaults to the appropriate sub implementation
     *
     * @param customizer the {@link TableDefaultsCustomizer} implementation
     */
    protected abstract void insertDefaults(@NonNull TableDefaultsCustomizer customizer);

}

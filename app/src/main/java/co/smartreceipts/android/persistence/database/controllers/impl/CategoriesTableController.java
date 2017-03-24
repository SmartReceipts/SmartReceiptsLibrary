package co.smartreceipts.android.persistence.database.controllers.impl;

import android.support.annotation.NonNull;

import co.smartreceipts.android.analytics.Analytics;
import co.smartreceipts.android.model.Category;
import co.smartreceipts.android.persistence.PersistenceManager;

public class CategoriesTableController extends AbstractTableController<Category> {

    public CategoriesTableController(@NonNull PersistenceManager persistenceManager, @NonNull Analytics analytics) {
        super(persistenceManager.getDatabase().getCategoriesTable(), analytics);
    }
}

package co.smartreceipts.android.workers.reports;

import android.content.Context;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import co.smartreceipts.android.model.Column;
import co.smartreceipts.android.model.ColumnDefinitions;
import co.smartreceipts.android.model.Distance;
import co.smartreceipts.android.model.Receipt;
import co.smartreceipts.android.model.Trip;
import co.smartreceipts.android.model.impl.columns.distance.DistanceColumnDefinitions;
import co.smartreceipts.android.persistence.DatabaseHelper;
import co.smartreceipts.android.persistence.PersistenceManager;
import co.smartreceipts.android.persistence.Preferences;
import co.smartreceipts.android.workers.reports.pdf.pdfbox.PdfBoxReportFile;
import wb.android.flex.Flex;
import wb.android.storage.StorageManager;

public class PdfBoxFullPdfReport extends PdfBoxAbstractReport {

    public PdfBoxFullPdfReport(@NonNull Context context, @NonNull PersistenceManager persistenceManager, Flex flex) {
        super(context, persistenceManager, flex);
    }

    protected PdfBoxFullPdfReport(@NonNull Context context, @NonNull DatabaseHelper db, @NonNull Preferences preferences, @NonNull StorageManager storageManager, Flex flex) {
        super(context, db, preferences, storageManager, flex);
    }

    @Override
    public void createSections(@NonNull Trip trip, PdfBoxReportFile pdfBoxReportFile) {
        final List<Receipt> receipts = new ArrayList<>(getDatabase().getReceiptsTable().getBlocking(trip, false));
        final List<Column<Receipt>> columns = getDatabase().getPDFTable().get().toBlocking().first();


        List<Distance> distances = null;
        List<Column<Distance>> distanceColumns = null;

        if (getPreferences().getPrintDistanceTable()) {
            distances = new ArrayList<>(getDatabase().getDistanceTable().getBlocking(trip, false));

            final ColumnDefinitions<Distance> distanceColumnDefinitions
                    = new DistanceColumnDefinitions(getContext(), getDatabase(), getPreferences(), getFlex(), true);
            distanceColumns = distanceColumnDefinitions.getAllColumns();
        }

        pdfBoxReportFile.addSection(
                pdfBoxReportFile.createReceiptsTableSection(trip, receipts, columns, distances,
                        distanceColumns));

        pdfBoxReportFile.addSection(
                pdfBoxReportFile.createReceiptsImagesSection(trip, receipts));
    }

}

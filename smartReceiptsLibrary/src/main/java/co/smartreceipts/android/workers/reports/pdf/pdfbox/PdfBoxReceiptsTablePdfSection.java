package co.smartreceipts.android.workers.reports.pdf.pdfbox;

import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import co.smartreceipts.android.R;
import co.smartreceipts.android.filters.LegacyReceiptFilter;
import co.smartreceipts.android.model.Column;
import co.smartreceipts.android.model.Distance;
import co.smartreceipts.android.model.Receipt;
import co.smartreceipts.android.model.Trip;
import co.smartreceipts.android.model.comparators.ReceiptDateComparator;
import co.smartreceipts.android.model.converters.DistanceToReceiptsConverter;
import co.smartreceipts.android.persistence.Preferences;
import co.smartreceipts.android.workers.reports.tables.PdfBoxTable;
import co.smartreceipts.android.workers.reports.tables.PdfBoxTableGenerator;

public class PdfBoxReceiptsTablePdfSection extends PdfBoxSection {

    private static final float EPSILON = 0.0001f;
    
    private final List<Receipt> mReceipts;
    private final List<Column<Receipt>> mReceiptColumns;

    private final List<Distance> mDistances;
    private final List<Column<Distance>> mDistanceColumns;

    private PdfBoxWriter mWriter;
    private final Preferences mPreferences;

    protected PdfBoxReceiptsTablePdfSection(@NonNull PdfBoxContext context,
                                            @NonNull Trip trip,
                                            @NonNull List<Receipt> receipts,
                                            @NonNull List<Column<Receipt>> receiptColumns,
                                            @NonNull List<Distance> distances,
                                            @NonNull List<Column<Distance>> distanceColumns) {
        super(context, trip);
        mReceipts = receipts;
        mDistances = distances;
        mReceiptColumns = receiptColumns;
        mPreferences = context.getPreferences();
        mDistanceColumns = distanceColumns;
    }



    @Override
    public void writeSection(@NonNull PDDocument doc) throws IOException {

        ReceiptsTotals totals = new ReceiptsTotals(mTrip,
                mReceipts, mDistances, mPreferences);

        // switch to landscape mode
        if (mPreferences.isReceiptsTableLandscapeMode()) {
            mContext.setPageSize(new PDRectangle(mContext.getPageSize().getHeight(),
                    mContext.getPageSize().getWidth()));
        }

        mWriter = new PdfBoxWriter(doc, mContext, new DefaultPdfBoxPageDecorations(mContext));

        writeHeader(mTrip, totals);

        mWriter.verticalJump(40);

        writeReceiptsTable(mReceipts);

        if (mPreferences.getPrintDistanceTable() && mDistances != null && !mDistances.isEmpty()) {
            mWriter.verticalJump(60);

            writeDistancesTable(mDistances);
        }

        mWriter.writeAndClose();

        // reset the page size if necessary
        if (mPreferences.isReceiptsTableLandscapeMode()) {
            mContext.setPageSize(new PDRectangle(mContext.getPageSize().getHeight(),
                    mContext.getPageSize().getWidth()));
        }
    }

    private void writeHeader(@NonNull Trip trip, @NonNull ReceiptsTotals data) throws IOException {

        mWriter.openTextBlock();

        mWriter.writeNewLine(mContext.getFont("FONT_TITLE"),
                trip.getName()
        );


        if (!data.mReceiptsPrice.equals(data.mNetPrice)) {
            mWriter.writeNewLine(mContext.getFont("FONT_DEFAULT"),
                    R.string.report_header_receipts_total,
                    data.mReceiptsPrice.getCurrencyFormattedPrice()
            );
        }

        if (mPreferences.includeTaxField()) {
            if (mPreferences.usePreTaxPrice() && data.mTaxPrice.getPriceAsFloat() > EPSILON) {
                mWriter.writeNewLine(mContext.getFont("FONT_DEFAULT"),
                        R.string.report_header_receipts_total_tax,
                        data.mTaxPrice.getCurrencyFormattedPrice()
                );

            } else if (!data.mNoTaxPrice.equals(data.mReceiptsPrice) &&
                    data.mNoTaxPrice.getPriceAsFloat() > EPSILON) {
                mWriter.writeNewLine(mContext.getFont("FONT_DEFAULT"),
                        R.string.report_header_receipts_total_no_tax,
                        data.mNoTaxPrice.getCurrencyFormattedPrice()
                );
            }
        }

        if (!mPreferences.onlyIncludeReimbursableReceiptsInReports() &&
                !data.mReimbursablePrice.equals(data.mReceiptsPrice)) {
            mWriter.writeNewLine(mContext.getFont("FONT_DEFAULT"),
                    R.string.report_header_receipts_total_reimbursable,
                    data.mReimbursablePrice.getCurrencyFormattedPrice()
            );
        }
        if (mDistances.size() > 0) {
            mWriter.writeNewLine(mContext.getFont("FONT_DEFAULT"),
                    R.string.report_header_distance_total,
                    data.mDistancePrice.getCurrencyFormattedPrice()
            );
        }

        mWriter.writeNewLine(mContext.getFont("FONT_DEFAULT"),
                R.string.report_header_gross_total,
                data.mNetPrice.getCurrencyFormattedPrice()
        );

        String fromToPeriod = mContext.getString(R.string.report_header_from,
                trip.getFormattedStartDate(mContext.getAndroidContext(), mPreferences.getDateSeparator()))
                + " "
                + mContext.getString(R.string.report_header_to,
                trip.getFormattedEndDate(mContext.getAndroidContext(), mPreferences.getDateSeparator()));

        mWriter.writeNewLine(mContext.getFont("FONT_DEFAULT"),
                fromToPeriod);


        if (mPreferences.getIncludeCostCenter() && !TextUtils.isEmpty(trip.getCostCenter())) {
            mWriter.writeNewLine(mContext.getFont("FONT_DEFAULT"),
                    R.string.report_header_cost_center,
                    trip.getCostCenter()
            );
        }
        if (!TextUtils.isEmpty(trip.getComment())) {
            mWriter.writeNewLine(
                    mContext.getFont("FONT_DEFAULT"),
                    R.string.report_header_comment,
                    trip.getComment()
            );
        }

        mWriter.closeTextBlock();
    }

    private void writeReceiptsTable(@NonNull List<Receipt> receipts) throws IOException {

        final List<Receipt> receiptsTableList = new ArrayList<>(receipts);
        if (mPreferences.getPrintDistanceAsDailyReceipt()) {
            receiptsTableList.addAll(
                    new DistanceToReceiptsConverter(mContext.getAndroidContext(), mPreferences)
                    .convert(mDistances));
            Collections.sort(receiptsTableList, new ReceiptDateComparator());
        }


        final PdfBoxTableGenerator<Receipt> pdfTableGenerator =
                new PdfBoxTableGenerator<>(mContext, mReceiptColumns,
                        new LegacyReceiptFilter(mPreferences), true, false);

        PdfBoxTable table = pdfTableGenerator.generate(receiptsTableList);

        mWriter.writeTable(table);
    }

    private void writeDistancesTable(@NonNull List<Distance> distances) throws IOException {


        final PdfBoxTableGenerator<Distance> pdfTableGenerator =
                new PdfBoxTableGenerator<>(mContext, mDistanceColumns,
                        null, true, true);


        PdfBoxTable table = pdfTableGenerator.generate(distances);

        mWriter.writeTable(table);
    }


}

package co.smartreceipts.android.workers.reports.pdf.pdfbox;

import android.support.annotation.NonNull;

import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;
import com.tom_roush.pdfbox.util.awt.AWTColor;

import java.io.IOException;


public class DefaultPdfBoxPageDecorations implements PdfBoxPageDecorations {


    private static final float HEADER_HEIGHT = 15.0f;
    private static final float HEADER_LINE_HEIGHT = 5.0f;

    private static final float FOOTER_LINE_HEIGHT = 3.0f;
    private static final float FOOTER_PADDING = 5.0f;
    private static final float FOOTER_HEIGHT = 24.0f;


    private final PdfBoxContext mContext;
    private final String mFooterText;


    DefaultPdfBoxPageDecorations(@NonNull PdfBoxContext context) {
        mContext = context;
        mFooterText = context.getPreferences().getPdfFooterText();
    }

    /**
     * Prints out a colored rectangle of height <code>HEADER_LINE_HEIGHT</code>
     * on the top part of the space reserved for the header, which has height
     * <code>HEADER_HEIGHT</code>.
     * <code>HEADER_LINE_HEIGHT</code> must be smaller than <code>HEADER_HEIGHT</code>
     * so that there is some padding space left naturally before the page content starts.
     * @param contentStream
     * @throws IOException
     */
    @Override
    public void writeHeader(@NonNull PDPageContentStream contentStream) throws IOException {

        PDRectangle rect = new PDRectangle(
                mContext.getPageMarginHorizontal(),
                mContext.getPageSize().getHeight()
                        - mContext.getPageMarginVertical()
                        - HEADER_LINE_HEIGHT,
                mContext.getPageSize().getWidth() - 2 * mContext.getPageMarginHorizontal(),
                HEADER_LINE_HEIGHT
        );
        contentStream.setNonStrokingColor(mContext.getColor(DefaultPdfBoxContext.COLOR_DARK_BLUE));
        contentStream.addRect(rect.getLowerLeftX(), rect.getLowerLeftY(), rect.getWidth(), rect.getHeight());
        contentStream.fill();
        contentStream.setNonStrokingColor(AWTColor.BLACK);
    }


    /**
     * Footer with height <code>FOOTER_HEIGHT</code> which consists of some padding of height
     * <code>FOOTER_PADDING</code>, a rectangle of height <code>FOOTER_LINE_HEIGHT</code>, followed
     * by a text message.
     * @param contentStream
     * @throws IOException
     */
    @Override
    public void writeFooter(PDPageContentStream contentStream) throws IOException {
        PDRectangle rect = new PDRectangle(
                mContext.getPageMarginHorizontal(),
                mContext.getPageMarginVertical()
                        + FOOTER_HEIGHT
                        - FOOTER_LINE_HEIGHT
                        - FOOTER_PADDING,
                mContext.getPageSize().getWidth() - 2 * mContext.getPageMarginHorizontal(),
                FOOTER_LINE_HEIGHT
        );
        contentStream.setNonStrokingColor(mContext.getColor(DefaultPdfBoxContext.COLOR_DARK_BLUE));
        contentStream.addRect(rect.getLowerLeftX(), rect.getLowerLeftY(), rect.getWidth(), rect.getHeight());
        contentStream.fill();
        contentStream.setNonStrokingColor(AWTColor.BLACK);


        contentStream.beginText();
        contentStream.newLineAtOffset(mContext.getPageMarginHorizontal(), mContext.getPageMarginVertical());
        contentStream.setFont(PDType1Font.HELVETICA_OBLIQUE, 12);
        contentStream.showText(mFooterText);
        contentStream.endText();
    }

    @Override
    public float getHeaderHeight() {
        return HEADER_HEIGHT;
    }

    @Override
    public float getFooterHeight() {
        return FOOTER_HEIGHT;
    }
}

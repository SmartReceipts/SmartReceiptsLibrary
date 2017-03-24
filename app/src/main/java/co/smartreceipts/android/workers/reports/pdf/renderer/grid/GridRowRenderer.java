package co.smartreceipts.android.workers.reports.pdf.renderer.grid;

import android.support.annotation.NonNull;

import com.google.common.base.Preconditions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import co.smartreceipts.android.workers.reports.pdf.pdfbox.PdfBoxWriter;
import co.smartreceipts.android.workers.reports.pdf.renderer.Renderer;
import co.smartreceipts.android.workers.reports.pdf.renderer.constraints.HeightConstraint;
import co.smartreceipts.android.workers.reports.pdf.renderer.constraints.WidthConstraint;
import co.smartreceipts.android.workers.reports.pdf.renderer.constraints.XPositionConstraint;
import co.smartreceipts.android.workers.reports.pdf.renderer.constraints.YPositionConstraint;
import co.smartreceipts.android.workers.reports.pdf.renderer.formatting.Padding;

public class GridRowRenderer extends Renderer {

    private final List<? extends Renderer> columns;

    public GridRowRenderer(@NonNull Renderer renderer) {
        this(Collections.singletonList(Preconditions.checkNotNull(renderer)));
    }

    public GridRowRenderer(@NonNull List<? extends Renderer> columns) {
        this.columns = new ArrayList<>(Preconditions.checkNotNull(columns));

        this.width = MATCH_PARENT;

        float layoutHeight = WRAP_CONTENT;
        for (final Renderer column : columns) {
            // We'll use match parent if any of them are set to that
            if (column.getHeight() == MATCH_PARENT) {
                layoutHeight = MATCH_PARENT;
                break;
            }
        }
        this.height = layoutHeight;
    }

    @Override
    public void measure() throws IOException {
        final float x = Preconditions.checkNotNull(getRenderingConstraints().getConstraint(XPositionConstraint.class, 0f));
        final float y = Preconditions.checkNotNull(getRenderingConstraints().getConstraint(YPositionConstraint.class, 0f));
        final float widthConstraint = Preconditions.checkNotNull(getRenderingConstraints().getConstraint(WidthConstraint.class));
        final Float heightConstraint = getRenderingConstraints().getConstraint(HeightConstraint.class);

        final Float padding = getRenderingFormatting().getFormatting(Padding.class);
        if (padding != null) {
            for (final Renderer renderer : columns) {
                renderer.getRenderingFormatting().addFormatting(new Padding(padding));
            }
        }

        final float perColumnWidth = widthConstraint / columns.size();
        final WidthConstraint perColumnWidthConstraint = new WidthConstraint(perColumnWidth);

        float measuredHeight = -1;
        for (int i = 0; i < columns.size(); i++) {
            final Renderer column = columns.get(i);
            // Space the columns evenly
            // TODO: Refactor this if we use dynamic formatting for the first tables
            if (heightConstraint != null) {
                column.getRenderingConstraints().addConstraint(new HeightConstraint(heightConstraint));
            }
            column.getRenderingConstraints().addConstraint(perColumnWidthConstraint);
            column.getRenderingConstraints().addConstraint(new XPositionConstraint(x + perColumnWidth * i));
            column.getRenderingConstraints().addConstraint(new YPositionConstraint(y));
            column.measure();
            measuredHeight = Math.max(measuredHeight, column.getHeight());
        }

        this.width = widthConstraint;
        this.height = measuredHeight;
    }

    @Override
    public void render(@NonNull PdfBoxWriter writer) throws IOException {
        for (final Renderer column : columns) {
            column.render(writer);
        }
    }
}

package co.smartreceipts.android.workers.reports.pdf.renderer.imagex;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;

import com.google.common.base.Preconditions;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import wb.android.storage.StorageManager;

public class ImagePDImageXFactory implements PDImageXFactory {

    private final PDDocument pdDocument;
    private final File file;

    public ImagePDImageXFactory(@NonNull PDDocument pdDocument, @NonNull File file) {
        this.pdDocument = Preconditions.checkNotNull(pdDocument);
        this.file = Preconditions.checkNotNull(file);
    }

    @NonNull
    public PDImageXObject get() throws IOException {
        final String fileExtension = StorageManager.getExtension(file);
        Preconditions.checkNotNull(fileExtension, "This file does not have a valid extension: " + file);

        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(file);
            if (fileExtension.toLowerCase().equals("jpg") || fileExtension.toLowerCase().equals("jpeg")) {
                return JPEGFactory.createFromStream(pdDocument, fileInputStream);
            } else if (fileExtension.toLowerCase().equals("png")) {
                Bitmap bitmap = null;
                try {
                    bitmap = BitmapFactory.decodeStream(fileInputStream);
                    return LosslessFactory.createFromImage(pdDocument, bitmap);
                } finally {
                    if (bitmap != null) {
                        bitmap.recycle();
                    }
                }
            } else {
                throw new IllegalArgumentException("Unknown file extension: " + fileExtension);
            }
        } finally {
            IOUtils.closeQuietly(fileInputStream);
        }
    }
}

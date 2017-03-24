package co.smartreceipts.android.ocr.apis.model;

import android.support.annotation.Nullable;

import java.io.Serializable;

public class OcrResponseField<T> implements Serializable {

    private final T data;
    private final Double confidenceLevel;

    public OcrResponseField(T data, Double confidenceLevel) {
        this.data = data;
        this.confidenceLevel = confidenceLevel;
    }

    @Nullable
    public T getData() {
        return data;
    }

    @Nullable
    public Double getConfidenceLevel() {
        return confidenceLevel;
    }

    @Override
    public String toString() {
        return "OcrResponseField{" +
                "data=" + data +
                ", confidenceLevel=" + confidenceLevel +
                '}';
    }
}

package co.smartreceipts.android.apis;

import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;

public class ExchangeRateServiceManager extends NetworkRequestManager<ExchangeRateService> {

    private static final String ENDPOINT = "https://openexchangerates.org";

    public ExchangeRateServiceManager(@NonNull FragmentManager fragmentManager) {
        super(fragmentManager, ENDPOINT, ExchangeRateService.class);
    }
}

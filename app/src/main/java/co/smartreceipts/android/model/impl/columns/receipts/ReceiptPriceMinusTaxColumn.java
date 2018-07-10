package co.smartreceipts.android.model.impl.columns.receipts;

import android.support.annotation.NonNull;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.List;

import co.smartreceipts.android.currency.PriceCurrency;
import co.smartreceipts.android.model.Price;
import co.smartreceipts.android.model.Receipt;
import co.smartreceipts.android.model.factory.PriceBuilderFactory;
import co.smartreceipts.android.model.impl.columns.AbstractColumnImpl;
import co.smartreceipts.android.settings.UserPreferenceManager;
import co.smartreceipts.android.settings.catalog.UserPreference;
import co.smartreceipts.android.sync.model.SyncState;

/**
 * Provides a column that returns the category code for a particular receipt
 */
public final class ReceiptPriceMinusTaxColumn extends AbstractColumnImpl<Receipt> {

    private final UserPreferenceManager userPreferenceManager;

    public ReceiptPriceMinusTaxColumn(int id, @NonNull SyncState syncState,
                                      @NonNull UserPreferenceManager userPreferenceManager,
                                      long customOrderId) {
        super(id, ReceiptColumnDefinitions.ActualDefinition.PRICE_MINUS_TAX, syncState, customOrderId);
        this.userPreferenceManager = Preconditions.checkNotNull(userPreferenceManager);
    }

    @Override
    public String getValue(@NonNull Receipt receipt) {
        return getPrice(receipt).getDecimalFormattedPrice();
    }
    
    @Override
    @NonNull
    public String getFooter(@NonNull List<Receipt> receipts) {
        if (!receipts.isEmpty()) {
            final PriceCurrency tripCurrency = receipts.get(0).getTrip().getTripCurrency();
            final List<Price> prices = new ArrayList<>();
            for (final Receipt receipt : receipts) {
                prices.add(getPrice(receipt));
            }
            final Price total =  new PriceBuilderFactory().setPrices(prices, tripCurrency).build();
            if (total.getCurrencyCodeCount() == 1) {
                return total.getDecimalFormattedPrice();
            } else {
                return total.getCurrencyCodeFormattedPrice();
            }
        } else {
            return "";
        }
    }

    private Price getPrice(@NonNull Receipt receipt) {
        if (userPreferenceManager.get(UserPreference.Receipts.UsePreTaxPrice)) {
            return receipt.getPrice();
        } else {
            final PriceBuilderFactory factory = new PriceBuilderFactory(receipt.getPrice());
            factory.setPrice(receipt.getPrice().getPrice().subtract(receipt.getTax().getPrice()));
            return factory.build();
        }
    }
}

package co.smartreceipts.android.model.impl.columns.categories

import co.smartreceipts.android.model.Price
import co.smartreceipts.android.model.factory.PriceBuilderFactory
import co.smartreceipts.android.model.impl.columns.AbstractColumnImpl
import co.smartreceipts.android.persistence.database.controllers.grouping.results.SumCategoryGroupingResult
import co.smartreceipts.android.sync.model.SyncState
import java.util.*


class CategoryTaxColumn(id: Int, syncState: SyncState) :
    AbstractColumnImpl<SumCategoryGroupingResult>(
        id,
        CategoryColumnDefinitions.ActualDefinition.TAX,
        syncState
    ) {

    override fun getValue(sumCategoryGroupingResult: SumCategoryGroupingResult): String? =
        sumCategoryGroupingResult.tax.decimalFormattedPrice

    override fun getFooter(rows: List<SumCategoryGroupingResult>): String {
        return if (!rows.isEmpty()) {
            val tripCurrency = rows[0].currency
            val prices = ArrayList<Price>()
            for (row in rows) {
                prices.add(row.tax)
            }
            PriceBuilderFactory().setPrices(prices, tripCurrency).build()
                .decimalFormattedPrice
        } else {
            ""
        }
    }
}

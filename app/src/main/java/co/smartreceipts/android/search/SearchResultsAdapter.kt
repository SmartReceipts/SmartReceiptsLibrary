package co.smartreceipts.android.search

import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.smartreceipts.android.date.DateFormatter
import co.smartreceipts.android.model.Receipt
import co.smartreceipts.android.model.Trip
import co.smartreceipts.android.search.viewholders.HeaderItem
import co.smartreceipts.android.search.viewholders.HeaderViewHolder
import co.smartreceipts.android.search.viewholders.ReceiptViewHolder
import co.smartreceipts.android.search.viewholders.TripViewHolder
import co.smartreceipts.core.sync.provider.SyncProvider
import com.squareup.picasso.Transformation

class SearchResultsAdapter(
    private val transformation: Transformation,
    private val dateFormatter: DateFormatter,
    private val syncProvider: SyncProvider,
    private val tripClickListener: (Trip) -> Unit,
    private val receiptClickListener: (Receipt) -> Unit,
) : ListAdapter<Any, RecyclerView.ViewHolder>(SearchResultsDiffUtilCallback()) {

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is HeaderItem -> TYPE_HEADER
            is Receipt -> TYPE_RECEIPT
            else -> TYPE_TRIP
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder.from(parent)
            TYPE_RECEIPT -> ReceiptViewHolder.from(parent)
            else -> TripViewHolder.from(parent)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)

        when (holder) {
            is HeaderViewHolder -> {
                holder.bind(item as HeaderItem)
            }
            is ReceiptViewHolder -> {
                holder.bind(item as Receipt, syncProvider, transformation, receiptClickListener)
            }
            is TripViewHolder -> {
                holder.bind(item as Trip, tripClickListener, {}, dateFormatter)
            }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_RECEIPT = 1
        private const val TYPE_TRIP = 2
    }
}
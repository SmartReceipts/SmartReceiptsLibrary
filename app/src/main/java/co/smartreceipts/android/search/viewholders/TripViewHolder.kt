package co.smartreceipts.android.search.viewholders

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import co.smartreceipts.android.R
import co.smartreceipts.android.databinding.ItemDefaultContentBinding
import co.smartreceipts.android.date.DateFormatter
import co.smartreceipts.android.model.Trip

class TripViewHolder(private val binding: ItemDefaultContentBinding) :
    RecyclerView.ViewHolder(binding.root) {


    fun bind(
        item: Trip,
        itemClickedListener: (Trip) -> Unit,
        itemLongClickedListener: (Trip) -> Unit,
        dateFormatter: DateFormatter
    ) {
        val context = binding.root.context
        val colorDefault = ContextCompat.getColor(context, R.color.text_primary_color)
        val colorText = ContextCompat.getColor(context, R.color.text_secondary_color)
        val start = dateFormatter.getFormattedDate(item.startDisplayableDate)
        val end = dateFormatter.getFormattedDate(item.endDisplayableDate)

        binding.root.setOnClickListener {
            itemClickedListener(item)
        }

        binding.root.setOnLongClickListener {
            itemLongClickedListener(item)
            true
        }

        binding.imageSelectionMarker.visibility = View.GONE
        binding.textName.setTextColor(colorDefault)
        binding.textPrice.setTextColor(colorDefault)
        binding.textPrice.text = item.price.currencyFormattedPrice
        binding.textName.text = item.name

        binding.textDetails.text =
            itemView.context.getString(R.string.trip_adapter_list_item_to, start, end)
        binding.textDetails.setTypeface(null, Typeface.NORMAL)
        binding.textDetails.setTextColor(colorText)
        binding.imageSyncState.visibility = View.GONE
    }

    companion object {
        fun from(parent: ViewGroup): TripViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding = ItemDefaultContentBinding.inflate(layoutInflater, parent, false)
            return TripViewHolder(binding)
        }
    }
}
package co.smartreceipts.android.search.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.smartreceipts.android.databinding.ItemRoundHeaderBinding

class HeaderViewHolder(private val binding: ItemRoundHeaderBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(item: HeaderItem) {
        binding.header.text = item.header
    }

    companion object {
        fun from(parent: ViewGroup): HeaderViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding = ItemRoundHeaderBinding.inflate(layoutInflater, parent, false)
            return HeaderViewHolder(binding)
        }
    }
}

data class HeaderItem(val header: String)
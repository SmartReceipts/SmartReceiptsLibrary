package co.smartreceipts.android.search.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.RecyclerView
import co.smartreceipts.android.R
import co.smartreceipts.android.databinding.ItemReceiptCardBinding
import co.smartreceipts.android.model.Receipt
import co.smartreceipts.core.sync.provider.SyncProvider
import com.squareup.picasso.Picasso
import com.squareup.picasso.Transformation

class ReceiptViewHolder(private val binding: ItemReceiptCardBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(
        item: Receipt,
        syncProvider: SyncProvider,
        transformation: Transformation,
        receiptClickListener: (Receipt) -> Unit
    ) {
        binding.root.setOnClickListener {
            receiptClickListener(item)
        }

        binding.content.apply {

            textName.text = item.name
            textPrice.text = item.price.currencyFormattedPrice

            textDetails.text = item.category.name
        }

        if (item.hasPDF()) {
            setIcon(binding.receiptImage, R.drawable.ic_pdf)
        } else if (item.hasImage() && item.file != null) {
            binding.receiptImage.setPadding(0, 0, 0, 0)

            Picasso.get()
                .load(item.file)
                .fit()
                .centerCrop()
                .transform(transformation)
                .into(binding.receiptImage)

        } else {
            setIcon(binding.receiptImage, R.drawable.ic_resource_import)
        }

        binding.content.imageSyncState.setOnClickListener(null)

        if (syncProvider == SyncProvider.GoogleDrive) {
            when {
                item.syncState.isSynced(SyncProvider.GoogleDrive) -> binding.content.imageSyncState.setImageResource(
                    R.drawable.ic_cloud_done_24dp
                )
                else -> binding.content.imageSyncState.setImageResource(R.drawable.ic_cloud_queue_24dp)
            }
        } else {
            binding.content.imageSyncState.setImageResource(R.drawable.ic_cloud_off_24dp)
        }
    }

    companion object {
        fun from(parent: ViewGroup): ReceiptViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding = ItemReceiptCardBinding.inflate(layoutInflater, parent, false)
            return ReceiptViewHolder(binding)
        }
    }
}

private fun setIcon(view: ImageView, @DrawableRes drawableRes: Int) {
    val context = view.context
    val drawable = ResourcesCompat.getDrawable(context.resources, drawableRes, context.getTheme())
    if (drawable != null) {
        drawable.mutate() // hack to prevent fab icon tinting (fab has drawable with the same src)
        DrawableCompat.setTint(
            drawable,
            ResourcesCompat.getColor(context.resources, R.color.receipt_image_tint, null)
        )
        val pixelPadding = context.resources.getDimensionPixelOffset(R.dimen.card_image_padding)
        view.setImageDrawable(drawable)
        view.setPadding(pixelPadding, pixelPadding, pixelPadding, pixelPadding)
    }
}
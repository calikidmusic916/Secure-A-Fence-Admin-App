package com.example.secureafenceadministrator.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.secureafenceadministrator.databinding.ItemGenericBinding
import java.util.Collections

class GenericAdapter<T>(
    items: List<T>,
    private val titleProvider: (T) -> String,
    private val subtitleProvider: (T) -> String,
    private val statusProvider: (T) -> String,
    private val descriptionProvider: ((T) -> String?)? = null,
    private val imageProvider: ((T) -> String?)? = null,
    private val rightImageResIdProvider: ((T) -> Int?)? = null,
    private val onItemClick: ((T) -> Unit)? = null
) : RecyclerView.Adapter<GenericAdapter.ViewHolder>() {

    val itemList: MutableList<T> = items.toMutableList()

    class ViewHolder(val binding: ItemGenericBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGenericBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = itemList[position]
        holder.binding.tvTitle.text = titleProvider(item)
        holder.binding.tvSubtitle.text = subtitleProvider(item)
        holder.binding.tvStatus.text = statusProvider(item)

        descriptionProvider?.let { provider ->
            val desc = provider(item)
            if (!desc.isNullOrEmpty()) {
                holder.binding.tvDescription.text = desc
                holder.binding.tvDescription.visibility = View.VISIBLE
            } else {
                holder.binding.tvDescription.visibility = View.GONE
            }
        } ?: run {
            holder.binding.tvDescription.visibility = View.GONE
        }
        
        imageProvider?.let { provider ->
            val imageUrl = provider(item)
            if (!imageUrl.isNullOrEmpty()) {
                val fullUrl = if (imageUrl.startsWith("/")) {
                    "https://secure-a-fence-backend.onrender.com$imageUrl"
                } else {
                    imageUrl
                }
                holder.binding.ivIcon.visibility = View.VISIBLE
                holder.binding.ivIcon.load(fullUrl)
            } else {
                holder.binding.ivIcon.visibility = View.GONE
            }
        } ?: run {
            holder.binding.ivIcon.visibility = View.GONE
        }

        rightImageResIdProvider?.let { provider ->
            val resId = provider(item)
            if (resId != null && resId != 0) {
                holder.binding.ivLogoEnd.visibility = View.VISIBLE
                holder.binding.ivLogoEnd.setImageResource(resId)
            } else {
                holder.binding.ivLogoEnd.visibility = View.GONE
            }
        } ?: run {
            holder.binding.ivLogoEnd.visibility = View.GONE
        }
        
        holder.itemView.setOnClickListener { onItemClick?.invoke(item) }
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition < 0 || toPosition < 0 || fromPosition >= itemList.size || toPosition >= itemList.size) return
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(itemList, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(itemList, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    override fun getItemCount(): Int = itemList.size
}

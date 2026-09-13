package com.example.secureafenceadministrator.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.secureafenceadministrator.databinding.ItemGenericBinding

class GenericAdapter<T>(
    private val items: List<T>,
    private val titleProvider: (T) -> String,
    private val subtitleProvider: (T) -> String,
    private val statusProvider: (T) -> String,
    private val descriptionProvider: ((T) -> String?)? = null,
    private val imageProvider: ((T) -> String?)? = null,
    private val onItemClick: ((T) -> Unit)? = null
) : RecyclerView.Adapter<GenericAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemGenericBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGenericBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
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
        
        holder.itemView.setOnClickListener { onItemClick?.invoke(item) }
    }

    override fun getItemCount(): Int = items.size
}

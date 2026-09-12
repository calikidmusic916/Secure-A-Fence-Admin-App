package com.example.secureafenceadministrator.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.secureafenceadministrator.databinding.ItemGenericBinding

class GenericAdapter<T>(
    private val items: List<T>,
    private val titleProvider: (T) -> String,
    private val subtitleProvider: (T) -> String,
    private val statusProvider: (T) -> String,
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
        
        imageProvider?.let { provider ->
            val imageUrl = provider(item)
            if (!imageUrl.isNullOrEmpty()) {
                // Ensure absolute URL if it starts with /
                val fullUrl = if (imageUrl.startsWith("/")) {
                    "https://secure-a-fence-backend.onrender.com$imageUrl"
                } else {
                    imageUrl
                }
                holder.binding.ivIcon.load(fullUrl)
            }
        }
        
        holder.itemView.setOnClickListener { onItemClick?.invoke(item) }
    }

    override fun getItemCount(): Int = items.size
}

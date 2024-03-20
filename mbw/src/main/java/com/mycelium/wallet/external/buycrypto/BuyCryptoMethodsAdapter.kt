package com.mycelium.wallet.external.buycrypto

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.mycelium.wallet.R
import com.mycelium.wallet.Utils
import com.mycelium.wallet.databinding.BuyCryptoMethodItemBinding
import com.mycelium.wallet.external.fiat.model.ChangellyMethod


class BuyCryptoMethodsAdapter(
    private val onItemSelected: (ChangellyMethod) -> Unit,
) : RecyclerView.Adapter<BuyCryptoMethodsAdapter.Holder>() {

    var methods: List<ChangellyMethod> = emptyList()
        set(value) {
            field = value
            selectedPosition = 0
            notifyDataSetChanged()
        }

    inner class Holder(val binding: BuyCryptoMethodItemBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(
            BuyCryptoMethodItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int = methods.size

    private var selectedPosition = 0

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val method = methods[position]
        val uri = Utils.tokenLogoPath(method.paymentMethod)
        holder.binding.apply {
            Glide.with(root)
                .load(uri)
                .apply(RequestOptions().fallback(R.drawable.ic_no_photo_placeholder))
                .into(logo)
            root.isSelected = selectedPosition == holder.absoluteAdapterPosition
            root.setOnClickListener {
                val oldPosition = selectedPosition
                notifyItemChanged(oldPosition)
                selectedPosition = holder.absoluteAdapterPosition
                onItemSelected(method)
                notifyItemChanged(selectedPosition)
            }
        }
    }
}

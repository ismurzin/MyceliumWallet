package com.mycelium.wallet.external.changelly2

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mycelium.wallet.databinding.ItemGiftboxSelectAccountBinding
import com.mycelium.wallet.external.fiat.model.ChangellyFiatCurrenciesResponse

class CurrencyFiatAdapter(
    private val currencies: List<ChangellyFiatCurrenciesResponse>,
    private val onSelect: (ChangellyFiatCurrenciesResponse) -> Unit,
) : RecyclerView.Adapter<CurrencyFiatAdapter.Holder>() {

    inner class Holder(val binding: ItemGiftboxSelectAccountBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(ItemGiftboxSelectAccountBinding.inflate(LayoutInflater.from(parent.context)))
    }

    override fun getItemCount(): Int = currencies.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = currencies[position]
        holder.binding.apply {
            label.text = item.name
            coinType.text = item.ticker
            root.setOnClickListener { onSelect(item) }
        }
    }
}
package com.mycelium.wallet.external.buycrypto

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.mycelium.wallet.R
import com.mycelium.wallet.databinding.BuyCryptoOfferItemBinding
import com.mycelium.wallet.external.fiat.model.ChangellyMethod
import com.mycelium.wallet.external.fiat.model.ChangellyOffer


class BuyCryptoOffersAdapter(
    private val onItemSelected: (ChangellyOffer) -> Unit,
) : RecyclerView.Adapter<BuyCryptoOffersAdapter.Holder>() {

    var currentMethod: ChangellyMethod? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    private val offers get() = currentMethod?.offers ?: emptyList()

    inner class Holder(val binding: BuyCryptoOfferItemBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(
            BuyCryptoOfferItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int = offers.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val offer = offers[position]
        val data = offer.data
        val error = offer.error
        val context = holder.binding.root.context
        val isBestOffer = position == 0 && data != null
        holder.binding.apply {
            providerName.text = offer.name
            bestOffer.isVisible = isBestOffer
            cardLayout.setBackgroundResource(
                if (isBestOffer) R.drawable.buy_crypto_offer_background_accent
                else R.drawable.buy_crypto_offer_background
            )
            Glide.with(root)
                .load(offer.iconUrl)
                .apply(
                    RequestOptions()
                        .fallback(R.drawable.ic_no_photo_placeholder)
                        .transforms(CircleCrop())
                )
                .into(icon)
            if (data != null) {
                root.setOnClickListener { onItemSelected(offer) }
                rate.isVisible = true
                errorTint.isVisible = false
                rate.text = context.getString(
                    R.string.buy_crypto_rate,
                    formatInvertedRate(data.invertedRate),
                    currentMethod?.currencyFrom,
                )
                amount.text = context.getString(
                    R.string.buy_crypto_amount,
                    data.amountExpectedTo,
                    currentMethod?.currencyTo,
                )
            }
            if (error != null) {
                rate.isVisible = false
                errorTint.isVisible = true
                amount.setText(error.messageId)
            }
        }
    }

    private fun formatInvertedRate(rate: String) = "%.2f".format(rate.toDouble())
}

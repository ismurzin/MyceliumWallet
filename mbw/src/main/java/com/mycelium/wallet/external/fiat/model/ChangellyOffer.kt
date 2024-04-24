package com.mycelium.wallet.external.fiat.model

import android.net.Uri
import androidx.annotation.StringRes
import com.mycelium.wallet.R
import java.io.Serializable

data class ChangellyMethod(
    val paymentMethod: String,
    val currencyFrom: String,
    val currencyTo: String,
    val amountExpectedTo: String?,
    val rate: String?,
    val invertedRate: String?,
    val offers: List<ChangellyOffer> = emptyList(),
) : Serializable

data class ChangellyOffer(
    val name: String,
    val iconUrl: Uri? = null,
    val providerCode: String,
    val data: ChangellyOfferData? = null,
    val error: OfferErrorType? = null,
) : Serializable

data class ChangellyOfferData(
    val rate: String,
    val invertedRate: String,
    val amountExpectedTo: String,
) : Serializable

enum class OfferErrorType(@StringRes val messageId: Int) {
    MIN(R.string.buy_crypto_error_min),
    MAX(R.string.buy_crypto_error_max),
    EXCHANGE_PAIR(R.string.buy_crypto_error_exchange),
    INVALID_OFFER(R.string.buy_crypto_error_invalid_offer),
    UNAVAILABLE(R.string.buy_crypto_error_unavailable),
    UNEXPECTED(R.string.buy_crypto_error_unexpected);

    companion object {
        fun fromResponse(response: ChangellyFiatOffersResponse): OfferErrorType {
            return when (response.errorType) {
                "limits" -> {
                    val details = response.errorDetails?.firstOrNull() ?: return UNEXPECTED
                    if (details.cause == "min") return MIN
                    return MAX
                }

                "currency" -> EXCHANGE_PAIR
                "invalidOffer" -> INVALID_OFFER
                "unavailable" -> UNAVAILABLE
                else -> UNEXPECTED
            }
        }
    }
}
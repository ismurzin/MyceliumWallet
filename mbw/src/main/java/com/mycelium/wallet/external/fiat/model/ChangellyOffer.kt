package com.mycelium.wallet.external.fiat.model

import android.net.Uri

data class ChangellyMethod(
    val paymentMethod: String,
    val paymentMethodName: String,
    val rate: String,
    val offers: List<ChangellyOffer> = emptyList(),
) {
    override fun hashCode(): Int {
        return paymentMethod.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ChangellyMethod
        return paymentMethod == other.paymentMethod
    }
}

data class ChangellyOffer(
    val name: String,
    val iconUrl: Uri?,
    val data: ChangellyOfferData? = null,
    val error: ChangellyOfferError? = null,
)

data class ChangellyOfferData(
    val rate: String,
    val amountExpectedTo: String,
    val currencyFrom: String,
    val currencyTo: String,
)

data class ChangellyOfferError(
    val type: ErrorType,
    val message: String = "",
) {
    enum class ErrorType {
        MIN,
        MAX,
        COUNTRY,
        UNEXPECTED,
    }

    companion object {
        fun fromResponse(response: ChangellyFiatOffersResponse): ChangellyOfferError {
            response.apply {
                // todo add more types and add message handling
                if (errorType == null || errorMessage == null || errorDetails.isNullOrEmpty()) {
                    return ChangellyOfferError(ErrorType.UNEXPECTED)
                }
                if (errorType == "limits") {
                    val details = errorDetails.first()
                    if (details.cause == "min") {
                        return ChangellyOfferError(ErrorType.MIN)
                    }
                    return ChangellyOfferError(ErrorType.MAX)
                }
                return ChangellyOfferError(ErrorType.UNEXPECTED)
            }
        }
    }
}
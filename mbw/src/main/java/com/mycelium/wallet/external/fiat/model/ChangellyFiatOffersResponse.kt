package com.mycelium.wallet.external.fiat.model

data class ChangellyFiatOffersResponse(
    val providerCode: String,
    val rate: String?,
    val invertedRate: String?,
    val fee: String?,
    val amountFrom: String?,
    val amountExpectedTo: String?,
    val paymentMethodOffers: List<ChangellyFiatPaymentMethodResponse>?,
    val errorType: String?,
    val errorMessage: String?,
    val errorDetails: List<ChangellyFiatPaymentError>?,
)

data class ChangellyFiatPaymentMethodResponse(
    val amountExpectedTo: String,
    val method: String,
    val methodName: String,
    val rate: String,
    val invertedRate: String,
    val fee: String,
)

data class ChangellyFiatPaymentError(
    val cause: String,
    val value: String,
)

package com.mycelium.wallet.external.fiat.model

data class ChangellyFiatOfferRequest(
    val externalOrderId: String,
    val externalUserId: String,
    val providerCode: String,
    val currencyFrom: String,
    val currencyTo: String,
    val amountFrom: String,
    val country: String,
    val walletAddress: String,
    val paymentMethod: String? = null,
    val state: String? = null,
    val ip: String? = null,
    val walletExtraId: String? = null,
    val metadata: Any? = null,
)

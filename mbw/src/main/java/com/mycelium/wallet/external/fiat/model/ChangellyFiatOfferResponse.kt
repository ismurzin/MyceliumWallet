package com.mycelium.wallet.external.fiat.model

data class ChangellyFiatOfferResponse(
    val redirectUrl: String,
    val orderId: String,
    val externalUserId: String,
    val externalOrderId: String,
    val providerCode: String,
    val currencyFrom: String,
    val currencyTo: String,
    val amountFrom: String,
    val country: String,
    val walletAddress: String,
    val createdAt: String,
    val paymentMethod: String?,
    val walletExtraId: String?,
    val userAgent: String?,
    val metadata: String?,
    val state: String?,
    val ip: String?,
)

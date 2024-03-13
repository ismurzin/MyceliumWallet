package com.mycelium.wallet.external.fiat.model

data class ChangellyFiatCurrenciesResponse(
    val ticker: String,
    val name: String,
    val type: String,
    val iconUrl: String,
    val precision: String,
)

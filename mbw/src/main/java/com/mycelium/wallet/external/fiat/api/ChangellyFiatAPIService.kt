package com.mycelium.wallet.external.fiat.api

import com.mycelium.wallet.external.fiat.model.ChangellyFiatCurrenciesResponse
import com.mycelium.wallet.external.fiat.model.ChangellyFiatOfferRequest
import com.mycelium.wallet.external.fiat.model.ChangellyFiatOfferResponse
import com.mycelium.wallet.external.fiat.model.ChangellyFiatOffersResponse
import com.mycelium.wallet.external.fiat.model.ChangellyFiatProvidersResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ChangellyFiatAPIService {

    @GET("providers")
    suspend fun getProviders(): List<ChangellyFiatProvidersResponse>

    @GET("currencies")
    suspend fun getCurrencies(
        @Query("type") type: String,
        @Query("providerCode") providerCode: String? = null,
    ): Response<List<ChangellyFiatCurrenciesResponse>>

    @GET("offers")
    suspend fun getOffers(
        @Query("currencyFrom") currencyFrom: String,
        @Query("currencyTo") currencyTo: String,
        @Query("amountFrom") amountFrom: String,
        @Query("country") country: String,
        @Query("providerCode") providerCode: String? = null,
        @Query("externalUserId") externalUserId: String? = null,
        @Query("state") state: String? = null,
        @Query("ip") ip: String? = null,
    ): List<ChangellyFiatOffersResponse>

    @POST("orders")
    suspend fun createOrder(
        @Body data: ChangellyFiatOfferRequest,
    ): Response<ChangellyFiatOfferResponse>

}
package com.mycelium.wallet.external.fiat.api

import com.mycelium.wallet.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ChangellyFiatRetrofitFactory {
    private const val BASE_URL = "https://fiat-api.changelly.com/v1/"

    private fun getHttpClient() =
        OkHttpClient.Builder().apply {
            addInterceptor(ChangellyFiatInterceptor())
            if (!BuildConfig.DEBUG) return@apply
            addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
        }.build()


    val api: ChangellyFiatAPIService =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(getHttpClient())
            .build()
            .create(ChangellyFiatAPIService::class.java)
}
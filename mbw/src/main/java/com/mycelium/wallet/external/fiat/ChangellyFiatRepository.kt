package com.mycelium.wallet.external.fiat

import com.mycelium.bequant.remote.doRequest
import com.mycelium.wallet.external.fiat.api.ChangellyFiatRetrofitFactory
import com.mycelium.wallet.external.fiat.model.ChangellyFiatCurrenciesResponse
import com.mycelium.wallet.external.fiat.model.ChangellyFiatOfferRequest
import com.mycelium.wallet.external.fiat.model.ChangellyFiatOfferResponse
import com.mycelium.wallet.external.fiat.model.ChangellyFiatOffersResponse
import com.mycelium.wallet.external.fiat.model.ChangellyFiatProvidersResponse
import kotlinx.coroutines.CoroutineScope

object ChangellyFiatRepository {
    private val api = ChangellyFiatRetrofitFactory.api

    fun getProviders(
        scope: CoroutineScope,
        success: (List<ChangellyFiatProvidersResponse>?) -> Unit,
        error: ((Int, String) -> Unit)? = null,
        finally: (() -> Unit)? = null,
    ) {
        doRequest(scope, { api.getProviders() }, success, error, finally)
    }

    fun getCurrencies(
        scope: CoroutineScope,
        type: FiatCurrencyType = FiatCurrencyType.FIAT,
        success: (List<ChangellyFiatCurrenciesResponse>?) -> Unit,
        error: ((Int, String) -> Unit)? = null,
        finally: (() -> Unit)? = null,
    ) {
        doRequest(scope, { api.getCurrencies(type.value) }, success, error, finally)
    }

    fun getOffers(
        scope: CoroutineScope,
        currencyFrom: String,
        currencyTo: String,
        amountFrom: String,
        country: String,
        providerCode: String? = null,
        externalUserId: String? = null,
        state: String? = null,
        ip: String? = null,
        success: (List<ChangellyFiatOffersResponse>?) -> Unit,
        error: ((Int, String) -> Unit)? = null,
        finally: (() -> Unit)? = null,
    ) {
        doRequest(scope, {
            api.getOffers(
                currencyFrom = currencyFrom,
                currencyTo = currencyTo,
                amountFrom = amountFrom,
                country = country,
                providerCode = providerCode,
                externalUserId = externalUserId,
                state = state,
                ip = ip,
            )
        }, success, error, finally)
    }

    fun createOrder(
        scope: CoroutineScope,
        externalOrderId: String,
        externalUserId: String,
        providerCode: String,
        currencyFrom: String,
        currencyTo: String,
        amountFrom: String,
        country: String,
        walletAddress: String,
        paymentMethod: String,
        success: (ChangellyFiatOfferResponse?) -> Unit,
        error: ((Int, String) -> Unit)? = null,
        finally: (() -> Unit)? = null,
    ) {
        val data = ChangellyFiatOfferRequest(
            externalOrderId = externalOrderId,
            externalUserId = externalUserId,
            providerCode = providerCode,
            currencyFrom = currencyFrom,
            currencyTo = currencyTo,
            amountFrom = amountFrom,
            country = country,
            walletAddress = walletAddress,
            paymentMethod = paymentMethod,
        )
        doRequest(scope, {
            api.createOrder(data)
        }, success, error, finally)
    }

    enum class FiatCurrencyType(val value: String) {
        FIAT("fiat"),
        CRYPTO("crypto"),
    }
}

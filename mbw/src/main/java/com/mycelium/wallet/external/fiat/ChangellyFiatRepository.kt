package com.mycelium.wallet.external.fiat

import com.mycelium.bequant.remote.doRequest
import com.mycelium.wallet.Utils
import com.mycelium.wallet.external.fiat.api.ChangellyFiatRetrofitFactory
import com.mycelium.wallet.external.fiat.model.ChangellyFiatCurrenciesResponse
import com.mycelium.wallet.external.fiat.model.ChangellyFiatOfferRequest
import com.mycelium.wallet.external.fiat.model.ChangellyFiatOfferResponse
import com.mycelium.wallet.external.fiat.model.ChangellyFiatOffersResponse
import com.mycelium.wallet.external.fiat.model.ChangellyFiatProvidersResponse
import com.mycelium.wallet.external.fiat.model.ChangellyMethod
import com.mycelium.wallet.external.fiat.model.ChangellyOffer
import com.mycelium.wallet.external.fiat.model.ChangellyOfferData
import com.mycelium.wallet.external.fiat.model.ChangellyOfferError
import kotlinx.coroutines.CoroutineScope
import java.util.Locale

object ChangellyFiatRepository {
    private val api = ChangellyFiatRetrofitFactory.api

    private var providers = emptyList<ChangellyFiatProvidersResponse>()

    fun getCurrencies(
        scope: CoroutineScope,
        type: FiatCurrencyType = FiatCurrencyType.FIAT,
        success: (List<ChangellyFiatCurrenciesResponse>?) -> Unit,
        error: ((Int, String) -> Unit)? = null,
        finally: (() -> Unit)? = null,
    ) {
        doRequest(scope, { api.getCurrencies(type.value) }, success, error, finally)
    }

    suspend fun getMethods(
        currencyFrom: String,
        currencyTo: String,
        amountFrom: String,
        country: String? = null,
        providerCode: String? = null,
        externalUserId: String? = null,
        state: String? = null,
        ip: String? = null,
    ): List<ChangellyMethod> {
        providers = providers.ifEmpty { api.getProviders() }
        val systemCountry = Locale.getDefault().country
        val defaultCountry = if (systemCountry.isNullOrBlank()) "US" else systemCountry
        val countryCode = if (country.isNullOrBlank()) defaultCountry else country
        val offers = api.getOffers(
            currencyFrom = currencyFrom,
            currencyTo = currencyTo,
            amountFrom = amountFrom,
            country = countryCode.toUpperCase(Locale.getDefault()),
            providerCode = providerCode,
            externalUserId = externalUserId,
            state = state,
            ip = ip,
        )
        val methodsMap = offers.flatMap { offer ->
            offer.paymentMethodOffer.orEmpty().map { method -> method to offer }
        }.groupBy(
            keySelector = { it.first },
            valueTransform = { it.second }
        ).mapValues { (_, offers) ->
            offers.toMutableList()
        }
        offers.filter { it.errorType != null }.forEach { offer ->
            methodsMap.values.forEach { it.add(offer) }
        }
        val methodsList = methodsMap.map { (methodResponse, offersResponse) ->
            ChangellyMethod(
                paymentMethod = methodResponse.method,
                paymentMethodName = methodResponse.methodName,
                rate = methodResponse.rate,
                offers = offersResponse.mapToChangellyOffer(currencyFrom, currencyTo),
            )
        }
        return methodsList
    }

    private fun List<ChangellyFiatOffersResponse>.mapToChangellyOffer(
        currencyFrom: String,
        currencyTo: String,
    ): List<ChangellyOffer> {
        return map { response ->
            val provider = providers.find { it.code == response.providerCode }
            if (provider == null) {
                val error = ChangellyOfferError(ChangellyOfferError.ErrorType.UNEXPECTED)
                return@map ChangellyOffer("Undefined", null, error = error)
            }
            val name = provider.name
            val iconUrl = Utils.tokenLogoPath(provider.code)
            val errorType = response.errorType
            val rate = response.rate
            val amountTo = response.amountExpectedTo
            if (errorType != null || rate == null || amountTo == null) {
                val error = ChangellyOfferError.fromResponse(response)
                return@map ChangellyOffer(name, iconUrl, error = error)
            }
            val data = ChangellyOfferData(
                rate,
                amountTo,
                currencyFrom,
                currencyTo,
            )
            return@map ChangellyOffer(name, iconUrl, data = data)
        }
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

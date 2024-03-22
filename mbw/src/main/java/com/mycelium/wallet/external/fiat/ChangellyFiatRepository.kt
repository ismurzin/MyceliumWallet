package com.mycelium.wallet.external.fiat

import com.mrd.bitlib.lambdaworks.crypto.Base64
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
import com.mycelium.wallet.external.fiat.model.OfferErrorType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlin.random.Random

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

    private suspend fun getProvidersDeferred(): Deferred<List<ChangellyFiatProvidersResponse>> {
        return withContext(Dispatchers.IO) { async { api.getProviders() } }
    }

    suspend fun getMethods(
        currencyFrom: String,
        currencyTo: String,
        amountFrom: String,
    ): List<ChangellyMethod> {
        // don't wait for the providers request to complete and send it concurrently
        val providersDeferred = if (providers.isEmpty()) getProvidersDeferred() else null
        val offers = api.getOffers(
            currencyFrom = currencyFrom,
            currencyTo = currencyTo,
            amountFrom = amountFrom,
            country = COUNTRY_ISO,
            state = US_STATE_ISO,
        )
        // await providers request if needed
        providers = providersDeferred?.await() ?: providers

        // each method corresponds list of offers with actual exchange rates
        val methodsMap = offers.flatMap { offer ->
            offer.paymentMethodOffer.orEmpty().map { method ->
                method to offer.copy(
                    rate = method.rate,
                    invertedRate = method.invertedRate,
                    amountExpectedTo = method.amountExpectedTo,
                )
            }
        }.groupBy(keySelector = { it.first.method }, valueTransform = { it.second }
        ).mapValues { (_, offers) -> offers.toMutableList() }

        // if all offers return error then there is no payment methods
        // so create list with one special payment method
        if (methodsMap.isEmpty()) {
            return listOf(
                ChangellyMethod(
                    paymentMethod = FAILED_PAYMENT_METHOD,
                    currencyFrom = currencyFrom,
                    currencyTo = currencyTo,
                    rate = null,
                    amountExpectedTo = null,
                    offers = offers.mapToChangellyOffers(),
                )
            )
        }

        // add failed offers to each payment method
        offers.filter { it.errorType != null }.forEach { offer ->
            methodsMap.values.forEach { it.add(offer) }
        }

        val methodsList = methodsMap.map { (method, offersResponse) ->
            val mappedOffers = offersResponse.mapToChangellyOffers()
            // best offer always first because of descending sorting
            val bestOffer = mappedOffers.firstOrNull()?.data
            ChangellyMethod(
                paymentMethod = method,
                currencyFrom = currencyFrom,
                currencyTo = currencyTo,
                rate = bestOffer?.rate,
                amountExpectedTo = bestOffer?.amountExpectedTo,
                offers = mappedOffers,
            )
        }
        return methodsList.sortedByDescending { it.rate }
    }

    private fun List<ChangellyFiatOffersResponse>.mapToChangellyOffers(): List<ChangellyOffer> {
        return map { response ->
            val code = response.providerCode
            val provider = providers.find { it.code == code }
            if (provider == null) {
                // error getting provider list - unexpected error
                val error = OfferErrorType.UNEXPECTED
                return@map ChangellyOffer(code, null, code, error = error)
            }
            val name = provider.name
            val iconUrl = Utils.tokenLogoPath(provider.code)
            return@map with(response) {
                if (errorType != null || rate == null || invertedRate == null || amountExpectedTo == null) {
                    val error = OfferErrorType.fromResponse(response)
                    return@with ChangellyOffer(name, iconUrl, code, error = error)
                }
                val data = ChangellyOfferData(rate, invertedRate, amountExpectedTo)
                return@with ChangellyOffer(name, iconUrl, code, data = data)
            }
        }.sortedByDescending { it.data?.rate }
    }

    fun createOrder(
        scope: CoroutineScope,
        providerCode: String,
        currencyFrom: String,
        currencyTo: String,
        amountFrom: String,
        walletAddress: String,
        paymentMethod: String,
        success: (ChangellyFiatOfferResponse?) -> Unit,
        error: ((Int, String) -> Unit)? = null,
        finally: (() -> Unit)? = null,
    ) {
        val data = ChangellyFiatOfferRequest(
            externalOrderId = getRandomBase64(),
            externalUserId = getRandomBase64(),
            providerCode = providerCode,
            currencyFrom = currencyFrom,
            currencyTo = currencyTo,
            amountFrom = amountFrom,
            country = COUNTRY_ISO,
            state = US_STATE_ISO,
            walletAddress = walletAddress,
            paymentMethod = paymentMethod,
        )
        doRequest(scope, {
            api.createOrder(data)
        }, success, error, finally)
    }

    private fun getRandomBase64() = Base64.encodeToString(Random.nextBytes(32), false)

    enum class FiatCurrencyType(val value: String) {
        FIAT("fiat"),
        CRYPTO("crypto"),
    }

    private const val COUNTRY_ISO = "US" // United States
    private const val US_STATE_ISO = "MO" // Missouri
    const val FAILED_PAYMENT_METHOD = "Failed"
}

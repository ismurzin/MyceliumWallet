package com.mycelium.wallet.external.buycrypto

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.viewModelScope
import com.mycelium.wallet.Utils
import com.mycelium.wallet.activity.util.toStringFriendlyWithUnit
import com.mycelium.wallet.external.AccountRepository
import com.mycelium.wallet.external.changelly2.remote.Changelly2Repository
import com.mycelium.wallet.external.fiat.ChangellyFiatRepository
import com.mycelium.wallet.external.fiat.model.ChangellyFiatCurrenciesResponse
import com.mycelium.wallet.external.fiat.model.ChangellyMethod
import com.mycelium.wapi.wallet.Util
import com.mycelium.wapi.wallet.WalletAccount
import com.mycelium.wapi.wallet.coins.CryptoCurrency
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale


class BuyCryptoViewModel(
    application: Application
) : AndroidViewModel(application), AccountRepository {

    override val fromAccount = MutableLiveData<WalletAccount<*>>()
    override val toAccount = MediatorLiveData<WalletAccount<*>>()
    override val sellValue = object : MutableLiveData<String>() {
        override fun setValue(value: String?) {
            if (this.value != value) {
                super.setValue(value)
            }
        }
    }

    override fun isSupported(coinType: CryptoCurrency) =
        cryptoCurrencies.contains(
            Util.trimTestnetSymbolDecoration(coinType.symbol).toLowerCase(Locale.ROOT)
        )

    private val preferences =
        application.getSharedPreferences(PREFERENCE_FILE, Context.MODE_PRIVATE)

    private var cryptoCurrencies = setOf("btc", "eth")
    var fiatCurrencies = emptyList<ChangellyFiatCurrenciesResponse>()
        private set

    val buyValue = MutableLiveData<String>()
    val keyboardActive = MutableLiveData(false)
    val methods = MutableLiveData<List<ChangellyMethod>>()
    val isLoading = MutableLiveData(false)
    val currentMethod = MutableLiveData<ChangellyMethod?>()

    init {
        initCurrencies()
    }

    private fun initCurrencies() {
        preferences.getStringSet(KEY_SUPPORT_COINS, null)?.let { cryptoCurrencies = it }
        Changelly2Repository.supportCurrenciesFull(viewModelScope, { currencies ->
            currencies?.result
                ?.filter { it.fixRateEnabled && it.enabled }
                ?.map { it.ticker }
                ?.toSet()?.let {
                    cryptoCurrencies = it
                    preferences.edit { putStringSet(KEY_SUPPORT_COINS, it) }
                }
        })
        ChangellyFiatRepository.getCurrencies(viewModelScope,
            ChangellyFiatRepository.FiatCurrencyType.FIAT,
            { currencies ->
                currencies?.let {
                    fiatCurrencies = it
                    fromFiat.value = it.first { fiat -> fiat.ticker == DEFAULT_FIAT_TICKER }
                }
            })
    }

    fun initAccounts() {
        toAccount.value = getToAccountForInit()
    }


    val fromFiat = MutableLiveData<ChangellyFiatCurrenciesResponse>()
    val fromChain = MutableLiveData("")
    val fromCurrency = Transformations.map(fromFiat) { it.ticker }

    val toCurrency = Transformations.map(toAccount) {
        it?.coinType ?: Utils.getBtcCoinType()
    }
    val toAddress = Transformations.map(toAccount) {
        it?.receiveAddress?.toString()
    }
    val toChain = Transformations.map(toAccount) {
        if (it?.basedOnCoinType != it?.coinType) it?.basedOnCoinType?.name else ""
    }
    val toBalance = Transformations.map(toAccount) {
        it?.accountBalance?.spendable?.toStringFriendlyWithUnit()
    }
    val toFiatBalance = Transformations.map(toAccount) {
        it?.accountBalance?.spendable?.let { value ->
            mbwManager.exchangeRateManager
                .get(value, mbwManager.getFiatCurrency(it.coinType))
                ?.toStringFriendlyWithUnit()
        }
    }

    val isValid = MediatorLiveData<Boolean>().apply {
        value = isValid()
        addSource(toAddress) { value = isValid() }
        addSource(sellValue) { value = isValid() }
        addSource(fromFiat) { value = isValid() }
    }

    fun selectMethod(method: ChangellyMethod) {
        currentMethod.value = method
    }

    private val getMethodsWithDebounce = debounce(viewModelScope, {
        val currencyFrom = fromCurrency.value ?: return@debounce
        val currencyTo = toCurrency.value?.symbol ?: return@debounce
        val amountFrom = sellValue.value ?: return@debounce
        val amount = amountFrom.toBigDecimalOrNull()?.toString() ?: return@debounce
        try {
            val data = ChangellyFiatRepository.getMethods(currencyFrom, currencyTo, amount)
            methods.value = data
            isLoading.value = false
        } catch (e: Exception) {
            // ignore cancellation exception
            if (e !is CancellationException) {
                isLoading.value = false
            }
        }
    })

    fun getMethods() {
        isLoading.value = true
        viewModelScope.launch {
            getMethodsWithDebounce()
        }
    }

    fun getFiatBalance(): String? {
        return toAccount.value?.accountBalance?.spendable?.let { value ->
            mbwManager.exchangeRateManager
                .get(value, mbwManager.getFiatCurrency(toAccount.value?.coinType))
                ?.toStringFriendlyWithUnit()
        }
    }

    private fun getToAccountForInit() =
        if (isSupported(mbwManager.selectedAccount.coinType)) mbwManager.selectedAccount
        else mbwManager.getWalletManager(false)
            .getAllActiveAccounts()
            .firstOrNull { it.canSpend() && isSupported(it.coinType) }

    private fun isValid(): Boolean {
        return try {
            val sellAmount = sellValue.value?.toDouble() ?: .0
            when {
                sellAmount == .0 -> false
                toAddress.value == null -> false
                fromFiat.value == null -> false
                else -> true
            }
        } catch (e: java.lang.NumberFormatException) {
            false
        }
    }

    fun refreshReceiveAccount() {
        if (!mbwManager.selectedAccount.canSpend()) return
        if (!isSupported(mbwManager.selectedAccount.coinType)) return
        toAccount.value = mbwManager.selectedAccount
    }

    private fun debounce(
        scope: CoroutineScope,
        destinationFunction: suspend () -> Unit,
        delay: Long = 1000L,
        exceptionHandler: CoroutineExceptionHandler? = null,
    ): () -> Unit {
        var debounceJob: Job? = null
        val job = suspend {
            kotlinx.coroutines.delay(delay)
            destinationFunction()
        }
        return {
            debounceJob?.cancel()
            debounceJob =
                if (exceptionHandler != null) scope.launch(exceptionHandler) { job() }
                else scope.launch { job() }
        }
    }

    companion object {
        const val PREFERENCE_FILE = "buy_crypto"
        const val KEY_SUPPORT_COINS = "coin_support_list"
        const val DEFAULT_FIAT_TICKER = "USD"
    }
}
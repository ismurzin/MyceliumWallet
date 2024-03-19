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
    private val preferences =
        application.getSharedPreferences(PREFERENCE_FILE, Context.MODE_PRIVATE)

    private var cryptoCurrencies = emptySet<String>()
    var fiatCurrencies = emptyList<ChangellyFiatCurrenciesResponse>()
        private set

    val buyValue = MutableLiveData<String>()
    val keyboardActive = MutableLiveData(false)
    val methods = MutableLiveData<List<ChangellyMethod>>()

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

    fun isValid(): Boolean {
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

    private fun getToAccountForInit() = Utils.sortAccounts(
        mbwManager.getWalletManager(false)
            .getAllActiveAccounts(), mbwManager.metadataStorage
    ).firstOrNull {
        isSupported(it.coinType)
    }

    override fun isSupported(coinType: CryptoCurrency) =
        cryptoCurrencies.contains(
            Util.trimTestnetSymbolDecoration(coinType.symbol).toLowerCase(Locale.ROOT)
        )

    companion object {
        const val PREFERENCE_FILE = "buy_crypto"
        const val KEY_SUPPORT_COINS = "coin_support_list"
        const val DEFAULT_FIAT_TICKER = "USD"
    }

    val getMethodsWithDebounce = Util.debounce(viewModelScope, { getMethods() })

    private suspend fun getMethods() {
        val currencyFrom = fromCurrency.value ?: return
        val currencyTo = toCurrency.value?.symbol ?: return
        val amountFrom = sellValue.value ?: return
        try {
            val data = ChangellyFiatRepository.getMethods(
                currencyFrom,
                currencyTo,
                amountFrom,
                "GE"
            )
            methods.value = data
        } catch (e: Exception) {
            // todo add error handling
        }
    }
}
package com.mycelium.wallet.external.buycrypto

import android.app.Application
import android.content.Context
import android.util.Log
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
import com.mycelium.wapi.wallet.Util
import com.mycelium.wapi.wallet.WalletAccount
import com.mycelium.wapi.wallet.coins.CryptoCurrency
import com.mycelium.wapi.wallet.coins.Value
import java.math.BigDecimal
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
    val errorKeyboard = MutableLiveData("")
    private val errorTransaction = MutableLiveData("")
    val keyboardActive = MutableLiveData(false)

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
                    fromFiat.value = it.first()
                }
            })
    }

    fun initAccounts() {
        toAccount.value = getToAccountForInit()
    }


    val error = MediatorLiveData<String>().apply {
        value = ""
        fun error() =
            when {
                keyboardActive.value == true -> ""
                errorKeyboard.value?.isNotEmpty() == true -> errorKeyboard.value
                errorTransaction.value?.isNotEmpty() == true -> errorTransaction.value
                else -> ""
            }
        addSource(errorKeyboard) {
            value = error()
        }
        addSource(errorTransaction) {
            value = error()
        }
        addSource(keyboardActive) {
            value = error()
        }
        addSource(toAccount) {
            errorKeyboard.value = ""
            errorTransaction.value = ""
        }
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

    val validateData = MediatorLiveData<Boolean>().apply {
        value = isValid()
        addSource(toAddress) {
            value = isValid()
        }
        addSource(sellValue) {
            value = isValid()
        }
    }

    fun isValid(): Boolean =
        try {
            errorTransaction.value = ""
            val amount = sellValue.value?.toBigDecimal()
            when {
                toAddress.value == null -> false
                amount == null -> false
                amount == BigDecimal.ZERO -> false
                else -> false
            }
        } catch (e: java.lang.NumberFormatException) {
            false
        }

    fun convert(value: Value) =
        " ~${
            mbwManager.exchangeRateManager.get(value, mbwManager.getFiatCurrency(value.type))
                ?.toStringFriendlyWithUnit() ?: ""
        }"


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

    }

    fun reset() {
        sellValue.value = ""
        buyValue.value = ""
        errorTransaction.value = ""
        errorKeyboard.value = ""
        toAccount.value = toAccount.value
    }
}
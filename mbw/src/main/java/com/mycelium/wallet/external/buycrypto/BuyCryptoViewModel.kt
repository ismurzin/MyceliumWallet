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
import com.mycelium.wallet.R
import com.mycelium.wallet.Utils
import com.mycelium.wallet.WalletApplication
import com.mycelium.wallet.activity.util.toStringFriendlyWithUnit
import com.mycelium.wallet.external.AccountRepository
import com.mycelium.wallet.external.changelly.model.FixRate
import com.mycelium.wallet.external.changelly2.remote.Changelly2Repository
import com.mycelium.wapi.wallet.Address
import com.mycelium.wapi.wallet.Transaction
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

    var currencies = setOf("ETH", "BTC")
    val exchangeInfo = MutableLiveData<FixRate>()
    val buyValue = MutableLiveData<String>()
    val errorKeyboard = MutableLiveData("")
    private val errorTransaction = MutableLiveData("")
    val errorRemote = MutableLiveData("")
    val keyboardActive = MutableLiveData(false)
    val rateLoading = MutableLiveData(false)


    init {
        initCurrencies()
    }

    private fun initCurrencies() {
        preferences.getStringSet(KEY_SUPPORT_COINS, null)?.let { currencies = it }
        Changelly2Repository.supportCurrenciesFull(viewModelScope, { currenciesFull ->
            currenciesFull?.result
                ?.filter { it.fixRateEnabled && it.enabled }
                ?.map { it.ticker }
                ?.toSet()?.let {
                    currencies = it
                    preferences.edit { putStringSet(KEY_SUPPORT_COINS, it) }
                }
        })
    }

    fun initAccounts() {
//        val isSupported = isSupported(mbwManager.selectedAccount.coinType)
//        fromAccount.value = if (isSupported) mbwManager.selectedAccount
//        else {
//            mbwManager.getWalletManager(false)
//                .getAllActiveAccounts()
//                .firstOrNull { it.canSpend() && isSupported(it.coinType) }
//        }
        toAccount.value = getToAccountForInit()
    }


    val error = MediatorLiveData<String>().apply {
        value = ""
        fun error() =
            when {
                keyboardActive.value == true -> ""
                errorKeyboard.value?.isNotEmpty() == true -> errorKeyboard.value
                errorTransaction.value?.isNotEmpty() == true -> errorTransaction.value
                errorRemote.value?.isNotEmpty() == true -> errorRemote.value
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
            errorRemote.value = ""
        }
    }


    val fromCurrency = MutableLiveData<String>()
    val fromChain = MutableLiveData("")

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

    val exchangeRateFrom = Transformations.map(exchangeInfo) {
        "1 ${it.from.toUpperCase(Locale.ROOT)} = "
    }

    val exchangeRateToValue = Transformations.map(exchangeInfo) {
        it.result.toPlainString()
    }

    val exchangeRateToCurrency = Transformations.map(exchangeInfo) {
        it.to.toUpperCase(Locale.ROOT)
    }

    val fiatBuyValue = Transformations.map(buyValue) { buy ->
        if (buy?.isNotEmpty() == true) {
            try {
                mbwManager.exchangeRateManager
                    .get(toCurrency.value?.value(buy), mbwManager.getFiatCurrency(toCurrency.value))
                    ?.toStringFriendlyWithUnit()?.let { "≈$it" }
            } catch (e: NumberFormatException) {
                "N/A"
            }
        } else {
            ""
        }
    }

    val minerFee = MutableLiveData("")

    val validateData = MediatorLiveData<Boolean>().apply {
        value = isValid()
        addSource(toAddress) {
            value = isValid()
        }
        addSource(sellValue) {
            value = isValid()
        }
        addSource(exchangeInfo) {
            value = isValid()
        }
        addSource(rateLoading) {
            value = isValid()
        }
    }

    fun isValid(): Boolean =
        try {
            errorTransaction.value = ""
            minerFee.value = ""
            val res = getApplication<WalletApplication>().resources
            val amount = sellValue.value?.toBigDecimal()
            when {
                toAddress.value == null -> false
                rateLoading.value == true -> false
                amount == null -> false
                amount == BigDecimal.ZERO -> false
                exchangeInfo.value?.minFrom != null && amount < exchangeInfo.value?.minFrom -> {
                    errorTransaction.value = res.getString(
                        R.string.exchange_min_msg,
                        exchangeInfo.value?.minFrom?.stripTrailingZeros()?.toPlainString(),
                        exchangeInfo.value?.from?.toUpperCase(Locale.ROOT)
                    )
                    false
                }

                exchangeInfo.value?.maxFrom != null && amount > exchangeInfo.value?.maxFrom -> {
                    errorTransaction.value = res.getString(
                        R.string.exchange_max_msg,
                        exchangeInfo.value?.maxFrom?.stripTrailingZeros()?.toPlainString(),
                        exchangeInfo.value?.from?.toUpperCase(Locale.ROOT)
                    )
                    false
                }

                else -> checkValidTransaction() != null
            }
        } catch (e: java.lang.NumberFormatException) {
            false
        }

    private fun checkValidTransaction(): Transaction? {
//        val res = getApplication<WalletApplication>().resources
//        val account = fromAccount.value!!
//        val value = account.coinType.value(sellValue.value!!)
//        if (value.equalZero()) {
//            return null
//        }
//        val feeEstimation = mbwManager.getFeeProvider(account.basedOnCoinType).estimation
//        try {
//            return prepateTx(account.dummyAddress, sellValue.value!!)
//                    ?.apply {
//                        minerFee.value =
//                                res.getString(R.string.miner_fee) + " " +
//                                        this.totalFee().toStringFriendlyWithUnit() + " " +
//                                        mbwManager.exchangeRateManager
//                                                .get(this.totalFee(), mbwManager.getFiatCurrency(this.type))
//                                                ?.toStringFriendlyWithUnit()?.let { "≈$it" }
//                    }
//        } catch (e: OutputTooSmallException) {
//            errorTransaction.value = res.getString(R.string.amount_too_small_short,
//                    Value.valueOf(account.coinType, TransactionUtils.MINIMUM_OUTPUT_VALUE).toStringWithUnit())
//        } catch (e: InsufficientFundsForFeeException) {
//            if (account is ERC20Account) {
//                val fee = feeEstimation.normal.times(account.typicalEstimatedTransactionSize.toBigInteger())
//                errorTransaction.value = res.getString(R.string.please_top_up_your_eth_account,
//                        account.ethAcc.label, fee.toStringFriendlyWithUnit(), convert(fee)) + TAG_ETH_TOP_UP
//            } else {
//                errorTransaction.value = res.getString(R.string.insufficient_funds_for_fee)
//            }
//        } catch (e: InsufficientFundsException) {
//            errorTransaction.value = res.getString(R.string.insufficient_funds)
//        } catch (e: BuildTransactionException) {
//            mbwManager.reportIgnoredException("MinerFeeException", e)
//            errorTransaction.value = res.getString(R.string.tx_build_error) + " " + e.message
//        } catch (e: Exception) {
//            errorTransaction.value = res.getString(R.string.tx_build_error) + " " + e.message
//        }
        return null
    }

    fun prepateTx(address: Address, amount: String): Transaction? {
//        val res = getApplication<WalletApplication>().resources
//        val account = fromAccount.value!!
//        val value = account.coinType.value(amount)
//        if (value.equalZero()) {
//            return null
//        }
//        val feeEstimation = mbwManager.getFeeProvider(account.basedOnCoinType).estimation
//        try {
//            return account.createTx(
//                    address,
//                    value,
//                    FeePerKbFee(feeEstimation.normal),
//                    null
//            )
//        } catch (e: OutputTooSmallException) {
//            errorTransaction.postValue(res.getString(R.string.amount_too_small_short,
//                    Value.valueOf(account.coinType, TransactionUtils.MINIMUM_OUTPUT_VALUE).toStringWithUnit()))
//        } catch (e: InsufficientFundsForFeeException) {
//            if (account is ERC20Account) {
//                val parentAccountBalance = account.ethAcc.accountBalance.spendable
//                val topUpForFee = feeEstimation.normal.times(account.typicalEstimatedTransactionSize.toBigInteger()) - parentAccountBalance
//                errorTransaction.postValue(res.getString(R.string.please_top_up_your_eth_account,
//                        account.ethAcc.label, topUpForFee.toStringFriendlyWithUnit(), convert(topUpForFee)) + TAG_ETH_TOP_UP)
//            } else {
//                errorTransaction.postValue(res.getString(R.string.insufficient_funds_for_fee))
//            }
//        } catch (e: InsufficientFundsException) {
//            errorTransaction.postValue(res.getString(R.string.insufficient_funds))
//        } catch (e: BuildTransactionException) {
//            mbwManager.reportIgnoredException("MinerFeeException", e)
//            errorTransaction.postValue(res.getString(R.string.tx_build_error) + " " + e.message)
//        } catch (e: Exception) {
//            errorTransaction.postValue(res.getString(R.string.tx_build_error) + " " + e.message)
//        }
        return null
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
        Log.wtf("MY_TAG", "bc: ${it.receiveAddress} ${isSupported(it.coinType)}");
        isSupported(it.coinType)
    }

    override fun isSupported(coinType: CryptoCurrency) =
        currencies.contains(
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
        errorRemote.value = ""
        errorKeyboard.value = ""
        toAccount.value = toAccount.value
    }
}
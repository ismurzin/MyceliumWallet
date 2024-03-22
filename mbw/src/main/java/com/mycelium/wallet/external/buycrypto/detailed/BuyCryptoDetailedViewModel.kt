package com.mycelium.wallet.external.buycrypto.detailed

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mycelium.wallet.MbwManager
import com.mycelium.wallet.WalletApplication
import com.mycelium.wallet.activity.util.toStringFriendlyWithUnit
import com.mycelium.wallet.external.fiat.ChangellyFiatRepository
import com.mycelium.wapi.wallet.coins.Balance
import com.mycelium.wapi.wallet.coins.CryptoCurrency

class BuyCryptoDetailedViewModel : ViewModel() {
    private val mbwManager = MbwManager.getInstance(WalletApplication.getInstance())
    private val _isLoading = MutableLiveData(false)
    private val _error = MutableLiveData<String>()
    val isLoading: LiveData<Boolean> = _isLoading
    val error: LiveData<String> = _error

    fun getFiatBalance(balance: Balance, coinType: CryptoCurrency): String? {
        return balance.spendable?.let { value ->
            mbwManager.exchangeRateManager
                .get(value, mbwManager.getFiatCurrency(coinType))
                ?.toStringFriendlyWithUnit()
        }
    }

    fun createOrder(
        providerCode: String,
        currencyFrom: String,
        currencyTo: String,
        amountFrom: String,
        walletAddress: String,
        paymentMethod: String,
        onSuccess: (String) -> Unit,
    ) {
        _isLoading.value = true
        ChangellyFiatRepository.createOrder(
            viewModelScope,
            providerCode,
            currencyFrom,
            currencyTo,
            amountFrom,
            walletAddress,
            paymentMethod,
            success = { response -> response?.let { onSuccess(it.redirectUrl) } },
            error = { _, _ -> _error.value = "Something went wrong" },
            finally = { _isLoading.value = false }
        )
    }
}

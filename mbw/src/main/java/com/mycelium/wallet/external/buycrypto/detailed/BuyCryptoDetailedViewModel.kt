package com.mycelium.wallet.external.buycrypto.detailed

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mycelium.wallet.external.fiat.ChangellyFiatRepository

class BuyCryptoDetailedViewModel : ViewModel() {
    private val _isLoading = MutableLiveData(false)
    private val _error = MutableLiveData<String>()
    val isLoading: LiveData<Boolean> = _isLoading
    val error: LiveData<String> = _error


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

package com.mycelium.wallet.external

import androidx.lifecycle.MutableLiveData
import com.mycelium.wallet.MbwManager
import com.mycelium.wallet.WalletApplication
import com.mycelium.wapi.wallet.WalletAccount
import com.mycelium.wapi.wallet.coins.CryptoCurrency

interface AccountRepository {
    val mbwManager: MbwManager
        get() = MbwManager.getInstance(WalletApplication.getInstance())

    val fromAccount: MutableLiveData<WalletAccount<*>>
    val toAccount: MutableLiveData<WalletAccount<*>>
    val sellValue: MutableLiveData<String>

    fun isSupported(coinType: CryptoCurrency): Boolean
}
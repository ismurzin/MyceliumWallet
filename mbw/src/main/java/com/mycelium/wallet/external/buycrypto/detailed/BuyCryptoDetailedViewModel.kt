package com.mycelium.wallet.external.buycrypto.detailed

import androidx.lifecycle.ViewModel
import com.mycelium.wallet.MbwManager
import com.mycelium.wallet.WalletApplication
import com.mycelium.wallet.activity.util.toStringFriendlyWithUnit
import com.mycelium.wapi.wallet.coins.Balance
import com.mycelium.wapi.wallet.coins.CryptoCurrency

class BuyCryptoDetailedViewModel : ViewModel() {
    private val mbwManager = MbwManager.getInstance(WalletApplication.getInstance())

    fun getFiatBalance(balance: Balance, coinType: CryptoCurrency): String? {
        return balance.spendable?.let { value ->
            mbwManager.exchangeRateManager
                .get(value, mbwManager.getFiatCurrency(coinType))
                ?.toStringFriendlyWithUnit()
        }
    }
}

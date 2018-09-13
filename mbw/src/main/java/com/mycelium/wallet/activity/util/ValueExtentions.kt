package com.mycelium.wallet.activity.util

import com.mrd.bitlib.util.CoinUtil
import com.mycelium.wallet.exchange.FiatType
import com.mycelium.wapi.wallet.coins.BitcoinMain
import com.mycelium.wapi.wallet.coins.BitcoinTest
import com.mycelium.wapi.wallet.coins.Value
import com.mycelium.wapi.wallet.coins.ValueType
import java.text.DecimalFormat


@JvmOverloads
fun Value.toStringWithUnit(denomination: CoinUtil.Denomination = CoinUtil.Denomination.BTC): String {
    CoinFormat.maximumFractionDigits = type.unitExponent
    return String.format("%s %s", toString(denomination), denomination.getUnicodeString(type.symbol))
}

@JvmOverloads
fun Value.toString(denomination: CoinUtil.Denomination = CoinUtil.Denomination.BTC): String {
    CoinFormat.maximumFractionDigits = type.unitExponent
    var result = valueAsBigDecimal
    //TODO maybe need other idea for fiat type
    if (type !is FiatType) {
        result = result.movePointRight(type.unitExponent - denomination.decimalPlaces)
    }
    return CoinFormat.format(result)
}

fun ValueType.isBtc(): Boolean {
    return this == BitcoinMain.get() || this == BitcoinTest.get()
}


private object CoinFormat : DecimalFormat() {
    init {
        groupingSize = 3
        isGroupingUsed = true
        maximumFractionDigits = 8
        val symbols = decimalFormatSymbols
        symbols.decimalSeparator = '.'
        symbols.groupingSeparator = ' '
        decimalFormatSymbols = symbols
    }
}
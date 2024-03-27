package com.mycelium.wallet.activity.main.model

import android.os.Bundle
import com.mycelium.wallet.activity.main.BuySellFragment
import java.util.Objects

class ActionButton @JvmOverloads constructor(
    val id: BuySellFragment.ACTION,
    var text: String,
    val icon: Int = 0,
    var iconUrl: String? = null,
    var args: Bundle = Bundle(),
    val actionType: ActionType? = null,
) {
    var textColor = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as ActionButton?
        return id == that!!.id &&
                icon == that.icon &&
                textColor == that.textColor &&
                text == that.text
    }

    override fun hashCode(): Int {
        return Objects.hash(id, icon, text, textColor)
    }

    enum class ActionType {
        BUY_SELL,
        MARKETPLACE,
        EXCHANGE;

        companion object {
            fun fromName(name: String): ActionType? {
                return when {
                    name.contains("exchange", ignoreCase = true) -> EXCHANGE
                    name.contains("buy", ignoreCase = true) -> BUY_SELL
                    name.contains("marketplace", ignoreCase = true) -> MARKETPLACE
                    else -> null
                }
            }
        }
    }
}

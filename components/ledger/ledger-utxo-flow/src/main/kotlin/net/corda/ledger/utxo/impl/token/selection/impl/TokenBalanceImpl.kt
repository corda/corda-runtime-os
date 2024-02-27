package net.corda.ledger.utxo.impl.token.selection.impl

import net.corda.v5.ledger.utxo.token.selection.TokenBalance
import java.math.BigDecimal
import java.util.Objects

class TokenBalanceImpl(private val availableBalance: BigDecimal, private val totalBalance: BigDecimal) : TokenBalance {
    override fun getAvailableBalance(): BigDecimal {
        return availableBalance
    }

    override fun getTotalBalance(): BigDecimal {
        return totalBalance
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TokenBalanceImpl

        return Objects.equals(availableBalance, other.availableBalance) &&
            Objects.equals(totalBalance, other.totalBalance)
    }

    override fun hashCode(): Int {
        var result = availableBalance.hashCode()
        result = 31 * result + totalBalance.hashCode()
        return result
    }
}

package net.corda.ledger.utxo.impl.token.selection.impl

import java.math.BigDecimal
import java.util.Objects
import net.corda.v5.ledger.utxo.token.selection.TokenBalance

class TokenBalanceImpl(private val balance: BigDecimal, private val balanceIncludingClaimedTokens: BigDecimal) : TokenBalance {
    override fun getBalance(): BigDecimal {
        return balance
    }

    override fun getBalanceIncludingClaimedTokens(): BigDecimal {
        return balanceIncludingClaimedTokens
    }

    override fun equals(other: Any?): Boolean {
        if(this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TokenBalanceImpl

        return Objects.equals(balance, other.balance)
                && Objects.equals(balanceIncludingClaimedTokens, other.balanceIncludingClaimedTokens)
    }

    override fun hashCode(): Int {
        var result = balance.hashCode()
        result = 31 * result + balanceIncludingClaimedTokens.hashCode()
        return result
    }
}

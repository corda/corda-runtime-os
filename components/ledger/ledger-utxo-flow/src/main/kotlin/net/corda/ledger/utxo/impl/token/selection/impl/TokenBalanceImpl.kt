package net.corda.ledger.utxo.impl.token.selection.impl

import java.math.BigDecimal
import net.corda.v5.ledger.utxo.token.selection.TokenBalance

class TokenBalanceImpl(private val balance: BigDecimal, private val balanceIncludingClaimedTokens: BigDecimal) : TokenBalance {
    override fun getBalance(): BigDecimal {
        return balance
    }

    override fun getBalanceIncludingClaimedTokens(): BigDecimal {
        return balanceIncludingClaimedTokens
    }
}

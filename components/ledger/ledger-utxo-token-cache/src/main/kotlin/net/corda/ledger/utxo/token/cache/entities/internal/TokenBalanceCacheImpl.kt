package net.corda.ledger.utxo.token.cache.entities.internal

import net.corda.v5.ledger.utxo.token.selection.TokenBalance
import java.math.BigDecimal

internal class TokenBalanceCacheImpl(private val availableBalance: BigDecimal, private val totalBalance: BigDecimal) : TokenBalance {
    override fun getAvailableBalance(): BigDecimal {
        return availableBalance
    }

    override fun getTotalBalance(): BigDecimal {
        return totalBalance
    }
}

package net.corda.ledger.utxo.impl.token.selection.factories

import java.math.BigDecimal
import net.corda.v5.ledger.utxo.token.selection.TokenBalance

/**
 * The [TokenBalanceFactory] creates instances for a [TokenBalance]
 */
interface TokenBalanceFactory {

    /**
     * Creates an instance of [TokenBalance]
     *
     * @param availableBalance The available balance for the pool of tokens based on the filter applied
     * @param totalBalance The total balance for the pool of tokens based on the filter applied
     *
     * @return A new instance of [TokenBalance]
     */
    fun createTokenBalance(availableBalance: BigDecimal, totalBalance: BigDecimal): TokenBalance
}

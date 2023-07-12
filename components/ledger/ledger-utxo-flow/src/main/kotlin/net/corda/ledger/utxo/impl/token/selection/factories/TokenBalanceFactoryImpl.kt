package net.corda.ledger.utxo.impl.token.selection.factories

import java.math.BigDecimal
import net.corda.ledger.utxo.impl.token.selection.impl.TokenBalanceImpl
import net.corda.v5.ledger.utxo.token.selection.TokenBalance
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component

@Component(service = [TokenBalanceFactory::class])
class TokenBalanceFactoryImpl @Activate constructor() : TokenBalanceFactory {
    override fun createTokenBalance(availableBalance: BigDecimal, totalBalance: BigDecimal): TokenBalance =
        TokenBalanceImpl(availableBalance, totalBalance)
}

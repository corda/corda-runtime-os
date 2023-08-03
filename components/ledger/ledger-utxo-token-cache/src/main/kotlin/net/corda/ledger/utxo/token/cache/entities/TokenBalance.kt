package net.corda.ledger.utxo.token.cache.entities

import java.math.BigDecimal

// This data class was created because if the class net.corda.v5.ledger.utxo.token.selection.TokenBalance
// is used, OSGI will report issues due to some dependencies which are not being exported
// but exist in the package net.corda.v5.ledger.utxo.token.selection.
data class TokenBalance(val availableBalance: BigDecimal, val totalBalance: BigDecimal)
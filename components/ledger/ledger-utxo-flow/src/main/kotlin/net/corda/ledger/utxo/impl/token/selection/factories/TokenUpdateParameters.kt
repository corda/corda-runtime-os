package net.corda.ledger.utxo.impl.token.selection.factories

import net.corda.v5.ledger.utxo.token.selection.ClaimedToken

// HACK: This class has been added for testing will be removed by CORE-5722 (ledger integration)
data class TokenUpdateParameters(
    val newTokens: List<ClaimedToken>,
    val consumedTokens: List<ClaimedToken>
)

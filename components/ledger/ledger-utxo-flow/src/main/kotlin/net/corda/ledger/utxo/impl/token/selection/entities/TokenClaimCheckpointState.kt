package net.corda.ledger.utxo.impl.token.selection.entities

import net.corda.ledger.utxo.impl.token.selection.impl.PoolKey

data class TokenClaimCheckpointState(
    val claimId: String,
    val poolKey: PoolKey
)

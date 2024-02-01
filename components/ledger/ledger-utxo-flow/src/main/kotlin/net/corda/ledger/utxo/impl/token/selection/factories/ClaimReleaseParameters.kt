package net.corda.ledger.utxo.impl.token.selection.factories

import net.corda.ledger.utxo.impl.token.selection.impl.PoolKey
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.utxo.StateRef

@CordaSerializable
data class ClaimReleaseParameters(
    val claimId: String,
    val poolKey: PoolKey,
    val usedTokens: List<StateRef>
)

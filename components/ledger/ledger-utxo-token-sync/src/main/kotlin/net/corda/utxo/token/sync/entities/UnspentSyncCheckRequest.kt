package net.corda.utxo.token.sync.entities

import net.corda.v5.ledger.utxo.StateRef
import net.corda.virtualnode.HoldingIdentity

data class UnspentSyncCheckRequest(
    override val holdingIdentity: HoldingIdentity,
    val tokensToCheck: List<StateRef>
) : SyncRequest

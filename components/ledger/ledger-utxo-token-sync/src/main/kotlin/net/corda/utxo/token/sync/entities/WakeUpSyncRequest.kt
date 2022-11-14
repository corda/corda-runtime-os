package net.corda.utxo.token.sync.entities

import net.corda.virtualnode.HoldingIdentity

data class WakeUpSyncRequest(
    override val holdingIdentity: HoldingIdentity
) : SyncRequest

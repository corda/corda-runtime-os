package net.corda.utxo.token.sync.entities

import net.corda.virtualnode.HoldingIdentity

data class FullSyncRequest(override val holdingIdentity: HoldingIdentity) : SyncRequest


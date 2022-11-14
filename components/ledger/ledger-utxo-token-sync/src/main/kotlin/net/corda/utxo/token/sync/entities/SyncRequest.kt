package net.corda.utxo.token.sync.entities

import net.corda.virtualnode.HoldingIdentity

interface SyncRequest {
    val holdingIdentity: HoldingIdentity
}

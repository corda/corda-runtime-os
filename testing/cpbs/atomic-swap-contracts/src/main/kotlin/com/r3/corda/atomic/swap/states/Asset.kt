package com.r3.corda.atomic.swap.states

import com.r3.corda.atomic.swap.contracts.AssetContract
import com.r3.corda.ledger.utxo.ownable.OwnableState
import net.corda.v5.ledger.utxo.BelongsToContract
import java.security.PublicKey

@BelongsToContract(AssetContract::class)
data class Asset(
    private val owner: PublicKey,
    val assetName: String,
    val assetId: String,
    private val participants: List<PublicKey>
) : OwnableState {

    override fun getParticipants(): List<PublicKey> {
        return participants
    }

    override fun getOwner(): PublicKey {
        return owner
    }

    fun withNewOwner(newOwner: PublicKey, newParticipants: List<PublicKey>): Asset {
        return Asset(newOwner, assetName, assetId, newParticipants)
    }
}
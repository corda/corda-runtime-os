package com.r3.corda.atomic.swap.states

import com.r3.corda.atomic.swap.contracts.AssetContract
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey

@BelongsToContract(AssetContract::class)
data class Asset (
    val owner : Member,
    val assetName: String,
    val assetId: String,
    private val participants: List<PublicKey>) : ContractState {

    override fun getParticipants(): List<PublicKey> {
        return participants
    }

    fun withNewOwner(newOwner: Member, newParticipants: List<PublicKey>): Asset {
        return Asset(newOwner, assetName, assetId, newParticipants)
    }
}
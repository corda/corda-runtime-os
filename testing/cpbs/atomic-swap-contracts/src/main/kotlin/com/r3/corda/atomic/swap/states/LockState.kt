package com.r3.corda.atomic.swap.states

import com.r3.corda.atomic.swap.contracts.LockContract
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey

@BelongsToContract(LockContract::class)
data class LockState (
    val creator : PublicKey,
    val receiver: PublicKey,
    val assetName: String,
    val assetId: String,
    private val participants: List<PublicKey>) : ContractState {

    override fun getParticipants(): List<PublicKey> {
        return participants
    }

    fun withNewOwner(newOwner: PublicKey, newParticipants: List<PublicKey>): LockState {
        return LockState(newOwner, receiver, assetName, assetId, newParticipants)
    }
}
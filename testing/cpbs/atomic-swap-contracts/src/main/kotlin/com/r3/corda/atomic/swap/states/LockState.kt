package com.r3.corda.atomic.swap.states

import com.r3.corda.atomic.swap.contracts.LockContract
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey

@BelongsToContract(LockContract::class)
data class LockState(
    val creator: PublicKey,
    val receiver: PublicKey,
    val assetId: String,
    private val participants: List<PublicKey>,
    val bool: Boolean = true
) : ContractState {

    override fun getParticipants(): List<PublicKey> {
        return participants
    }
}
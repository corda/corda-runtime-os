package com.r3.corda.atomic.swap.states

import com.r3.corda.atomic.swap.contracts.LockContract
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey
import java.time.Instant

@BelongsToContract(LockContract::class)
data class LockState(
    val creator: PublicKey,
    val receiver: PublicKey,
    val assetId: String,
    val timeWindow: Instant,
    private val participants: List<PublicKey>,
    val transactionIdToVerify: SecureHash,
    val notaryPublicKey: PublicKey
) : ContractState {

    override fun getParticipants(): List<PublicKey> {
        return participants
    }
}
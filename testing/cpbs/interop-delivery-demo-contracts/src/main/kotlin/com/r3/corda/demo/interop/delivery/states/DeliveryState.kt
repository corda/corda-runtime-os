package com.r3.corda.demo.interop.delivery.states

import com.r3.corda.demo.interop.delivery.contracts.DeliveryContract
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey
import java.util.UUID


// Required for serialisation
data class DehydratedDeliveryState(
    val amount: Int,
    val issuerName: String,
    val ownerName: String,
    val participants: List<ByteArray>
)

@BelongsToContract(DeliveryContract::class)
data class DeliveryState (
    val amount: Int,
    val issuer: MemberX500Name,
    val owner: MemberX500Name,
    val linearId: UUID,
    private val participants: List<PublicKey>
) : ContractState {

    fun dehydrate(): DehydratedDeliveryState {
        val participantBytes = participants.map { it.encoded }
        return DehydratedDeliveryState(amount, issuer.toString(), owner.toString(), participantBytes)
    }

    fun withNewOwner(newOwner: MemberX500Name, newParticipants: List<PublicKey>): DeliveryState {
        return DeliveryState(amount, owner, newOwner, linearId, newParticipants)
    }

    override fun getParticipants(): List<PublicKey> {
        return participants
    }
}

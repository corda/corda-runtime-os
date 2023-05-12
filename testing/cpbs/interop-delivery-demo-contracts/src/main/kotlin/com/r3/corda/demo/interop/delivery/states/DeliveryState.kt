package com.r3.corda.demo.interop.delivery.states

import com.r3.corda.demo.interop.delivery.contracts.DeliveryContract
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey
import java.util.UUID


@BelongsToContract(DeliveryContract::class)
data class DeliveryState (
    val amount: Int,
    val issuer: MemberX500Name,
    val owner: MemberX500Name,
    val linearId: UUID,
    private val participants: List<PublicKey>
) : ContractState {

    fun withNewOwner(newOwner: MemberX500Name, newParticipants: List<PublicKey>): DeliveryState {
        return DeliveryState(amount, owner, newOwner, linearId, newParticipants)
    }

    override fun getParticipants(): List<PublicKey> {
        return participants
    }
}

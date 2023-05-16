package com.r3.corda.demo.interop.payment.states

import com.r3.corda.demo.interop.payment.contracts.PaymentContract
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey
import java.util.UUID


data class DehydratedPaymentState(
    val amount: Int,
    val issuerName: String,
    val ownerName: String,
    val participants: List<ByteArray>
)

@BelongsToContract(PaymentContract::class)
data class PaymentState(
    val amount: Int,
    val issuer: MemberX500Name,
    val owner: MemberX500Name,
    val linearId: UUID,
    private val participants: List<PublicKey>
) : ContractState {

    fun dehydrate(): DehydratedPaymentState {
        val participantBytes = participants.map { it.encoded }
        return DehydratedPaymentState(amount, issuer.toString(), owner.toString(), participantBytes)
    }

    fun withNewOwner(newOwner: MemberX500Name, newParticipants: List<PublicKey>): PaymentState {
        return PaymentState(amount, owner, newOwner, linearId, newParticipants)
    }

    override fun getParticipants(): List<PublicKey> {
        return participants
    }
}

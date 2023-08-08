package com.r3.corda.demo.interop.tokens.states

import com.r3.corda.demo.interop.tokens.contracts.TokenContract
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey
import java.util.UUID


// Required for serialisation
data class DehydratedTokenState(
    val amount: Int,
    val issuerName: String,
    val ownerName: String,
    val participants: List<ByteArray>
)

@BelongsToContract(TokenContract::class)
data class TokenState (
    val amount: Int,
    val issuer: MemberX500Name,
    val owner: MemberX500Name,
    val linearId: UUID,
    private val participants: List<PublicKey>
) : ContractState {

    fun dehydrate(): DehydratedTokenState {
        val participantBytes = participants.map { it.encoded }
        return DehydratedTokenState(amount, issuer.toString(), owner.toString(), participantBytes)
    }

    fun withNewOwner(newOwner: MemberX500Name, newParticipants: List<PublicKey>): TokenState {
        return TokenState(amount, owner, newOwner, linearId, newParticipants)
    }

    override fun getParticipants(): List<PublicKey> {
        return participants
    }
}

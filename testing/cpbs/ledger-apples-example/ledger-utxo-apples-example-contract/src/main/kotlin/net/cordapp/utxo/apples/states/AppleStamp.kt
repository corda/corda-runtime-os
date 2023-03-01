package net.cordapp.utxo.apples.states

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import net.cordapp.utxo.apples.contracts.AppleStampContract
import java.security.PublicKey
import java.util.UUID

@BelongsToContract(AppleStampContract::class)
@CordaSerializable
data class AppleStamp(
    val id: UUID,
    val stampDesc: String,
    val issuer: Party,
    val holder: Party,
    private val participants: List<PublicKey>
) : ContractState {
    override fun getParticipants() = participants
}

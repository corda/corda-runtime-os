package net.cordapp.testing.packagingverification.contract

import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey

@BelongsToContract(SimpleContract::class)
class SimpleState(val value: Long, private val participants: List<PublicKey>, val issuer: MemberX500Name) : ContractState {
    override fun getParticipants() = participants
}

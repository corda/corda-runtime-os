package net.cordapp.testing.packagingverification.contract

import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey

@BelongsToContract(SimpleContract::class)
class SimpleState(val value: Long, val issuer: SecureHash, private val participants: List<PublicKey>) : ContractState {
    override fun getParticipants() = participants
}

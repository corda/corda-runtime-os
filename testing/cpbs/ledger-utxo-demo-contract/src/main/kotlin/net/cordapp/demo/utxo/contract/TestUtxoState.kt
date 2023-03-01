package net.cordapp.demo.utxo.contract

import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey

@BelongsToContract(TestContract::class)
class TestUtxoState(
    val testField: String,
    private val participants: List<PublicKey>,
    val participantNames: List<String>
) : ContractState {

    override fun getParticipants(): List<PublicKey> {
        return participants
    }
}

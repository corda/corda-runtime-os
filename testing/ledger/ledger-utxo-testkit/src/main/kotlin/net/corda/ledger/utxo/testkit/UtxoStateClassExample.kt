package net.corda.ledger.utxo.testkit

import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey

@BelongsToContract(UtxoContractExample::class)
class UtxoStateClassExample(
    val testField: String,
    override val participants: List<PublicKey>
) : ContractState {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UtxoStateClassExample) return false
        if (other.testField != testField) return false
        if (other.participants.size != participants.size) return false
        return other.participants.containsAll(participants)
    }

    override fun hashCode(): Int = testField.hashCode() + participants.hashCode() * 31
}
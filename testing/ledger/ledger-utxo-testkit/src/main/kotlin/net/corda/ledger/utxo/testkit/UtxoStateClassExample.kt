package net.corda.ledger.utxo.testkit

import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey
import java.util.Objects

@BelongsToContract(UtxoContractExample::class)
class UtxoStateClassExample(
    val testField: String,
    override val participants: List<PublicKey>
) : ContractState {
    override fun equals(other: Any?): Boolean =
        (this === other) || (
            (other is UtxoStateClassExample) &&
                (other.testField == testField) &&
                (other.participants.size == participants.size) &&
                other.participants.containsAll(participants)
            )

    override fun hashCode(): Int = Objects.hash(testField, participants)
}
package net.cordapp.testing.ledger.utxo

import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import java.security.PublicKey
import java.util.Objects

@Suppress("Unused")
@BelongsToContract(UtxoContractExample::class)
class UtxoStateClassExample(val testField: String, private val participants: List<PublicKey>) : ContractState {

    override fun getParticipants(): List<PublicKey> {
        return participants
    }

    override fun equals(other: Any?): Boolean {
        return this === other
                || other is UtxoStateClassExample
                && other.testField == testField
                && other.participants == participants
    }

    override fun hashCode(): Int = Objects.hash(testField, participants)
}

class UtxoContractExample : Contract {
    override fun verify(transaction: UtxoLedgerTransaction) {
    }
}

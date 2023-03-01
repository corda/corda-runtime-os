package net.corda.simulator.runtime.ledger.utxo

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.ledger.utxo.transaction.getOutputStates
import java.security.PublicKey
import java.time.Instant

fun toSignatureWithMetadata(key: PublicKey, timestamp: Instant = Instant.now()) = DigitalSignatureAndMetadata(
    DigitalSignature.WithKey(key, "some bytes".toByteArray(), mapOf()),
    DigitalSignatureMetadata(timestamp, SignatureSpec("dummySignatureName"), mapOf())
)

@BelongsToContract(TestUtxoContract::class)
class TestUtxoState(
    val name: String,
    override val participants: List<PublicKey>
) : ContractState {
    override fun equals(other: Any?): Boolean {
        if(other !is TestUtxoState) return false
        return name == other.name && participants == other.participants
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}

class TestUtxoCommand: Command

class TestUtxoContract: Contract {
    override fun verify(transaction: UtxoLedgerTransaction) {
        if(transaction.getOutputStates<TestUtxoState>().first().name == "Faulty State")
            throw IllegalArgumentException("Faulty State Detected")
    }
}
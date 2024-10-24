package net.corda.ledger.utxo.flow.impl.transaction.serializer.kryo

import net.corda.ledger.lib.utxo.flow.impl.transaction.UtxoSignedLedgerTransaction
import net.corda.ledger.lib.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.lib.utxo.flow.impl.transaction.verifier.UtxoSignedLedgerTransactionImpl
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionInternal
import net.corda.sandbox.type.SandboxConstants.CORDA_UNINJECTABLE_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.serialization.checkpoint.CheckpointInput
import net.corda.serialization.checkpoint.CheckpointInternalCustomSerializer
import net.corda.serialization.checkpoint.CheckpointOutput
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [ CheckpointInternalCustomSerializer::class, UsedByFlow::class ],
    property = [ CORDA_UNINJECTABLE_SERVICE ],
    scope = PROTOTYPE
)
class UtxoSignedLedgerTransactionKryoSerializer : CheckpointInternalCustomSerializer<UtxoSignedLedgerTransaction>, UsedByFlow {
    override val type: Class<UtxoSignedLedgerTransaction> get() = UtxoSignedLedgerTransaction::class.java

    override fun write(output: CheckpointOutput, obj: UtxoSignedLedgerTransaction) {
        output.writeClassAndObject(obj.ledgerTransaction)
        output.writeClassAndObject(obj.signedTransaction)
    }

    override fun read(input: CheckpointInput, type: Class<out UtxoSignedLedgerTransaction>): UtxoSignedLedgerTransaction {
        val ledgerTransaction = input.readClassAndObject() as UtxoLedgerTransactionInternal
        val signedTransaction = input.readClassAndObject() as UtxoSignedTransactionInternal
        return UtxoSignedLedgerTransactionImpl(ledgerTransaction, signedTransaction)
    }
}

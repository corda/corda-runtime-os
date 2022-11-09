package net.corda.ledger.utxo.flow.impl.transaction.serializer.kryo

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.transaction.TransactionSignatureService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionImpl
import net.corda.sandbox.type.UsedByFlow
import net.corda.serialization.checkpoint.CheckpointInput
import net.corda.serialization.checkpoint.CheckpointInternalCustomSerializer
import net.corda.serialization.checkpoint.CheckpointOutput
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.util.uncheckedCast
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Component(service = [CheckpointInternalCustomSerializer::class, UsedByFlow::class], scope = ServiceScope.PROTOTYPE)
class UtxoSignedTransactionKryoSerializer @Activate constructor(
    @Reference(service = SerializationService::class)
    private val serialisationService: SerializationService,
    @Reference(service = TransactionSignatureService::class)
    private val transactionSignatureService: TransactionSignatureService
) : CheckpointInternalCustomSerializer<UtxoSignedTransaction>, UsedByFlow {
    override val type: Class<UtxoSignedTransaction> get() = UtxoSignedTransaction::class.java

    override fun write(output: CheckpointOutput, obj: UtxoSignedTransaction) {
        output.writeClassAndObject((obj as UtxoSignedTransactionImpl).wireTransaction)
        output.writeClassAndObject(obj.signatures)
    }

    override fun read(input: CheckpointInput, type: Class<UtxoSignedTransaction>): UtxoSignedTransaction {
        val wireTransaction = input.readClassAndObject() as WireTransaction
        val signatures: List<DigitalSignatureAndMetadata> = uncheckedCast(input.readClassAndObject())
        return UtxoSignedTransactionImpl(
            serialisationService,
            transactionSignatureService,
            wireTransaction,
            signatures
        )
    }
}
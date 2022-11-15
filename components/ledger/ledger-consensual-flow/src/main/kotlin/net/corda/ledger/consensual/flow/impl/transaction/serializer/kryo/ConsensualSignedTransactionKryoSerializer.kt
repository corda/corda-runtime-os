package net.corda.ledger.consensual.flow.impl.transaction.serializer.kryo

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.transaction.TransactionSignatureService
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionImpl
import net.corda.sandbox.type.UsedByFlow
import net.corda.serialization.checkpoint.CheckpointInput
import net.corda.serialization.checkpoint.CheckpointInternalCustomSerializer
import net.corda.serialization.checkpoint.CheckpointOutput
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.util.uncheckedCast
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(service = [ CheckpointInternalCustomSerializer::class, UsedByFlow::class ], scope = PROTOTYPE)
class ConsensualSignedTransactionKryoSerializer @Activate constructor(
    @Reference(service = SerializationService::class)
    private val serialisationService: SerializationService,
    @Reference(service = TransactionSignatureService::class)
    private val transactionSignatureService: TransactionSignatureService
) : CheckpointInternalCustomSerializer<ConsensualSignedTransaction>, UsedByFlow {
    override val type: Class<ConsensualSignedTransaction> get() = ConsensualSignedTransaction::class.java

    override fun write(output: CheckpointOutput, obj: ConsensualSignedTransaction) {
        output.writeClassAndObject((obj as ConsensualSignedTransactionImpl).wireTransaction)
        output.writeClassAndObject(obj.signatures)
    }

    override fun read(input: CheckpointInput, type: Class<ConsensualSignedTransaction>): ConsensualSignedTransaction {
        val wireTransaction = input.readClassAndObject() as WireTransaction
        val signatures: List<DigitalSignatureAndMetadata> = uncheckedCast(input.readClassAndObject())
        return ConsensualSignedTransactionImpl(
            serialisationService,
            transactionSignatureService,
            wireTransaction,
            signatures
        )
    }
}
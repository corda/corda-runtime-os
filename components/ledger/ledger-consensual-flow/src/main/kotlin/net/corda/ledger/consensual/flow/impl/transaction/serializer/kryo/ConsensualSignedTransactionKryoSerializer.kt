package net.corda.ledger.consensual.flow.impl.transaction.serializer.kryo

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.v5.ledger.common.transaction.TransactionSignatureService
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionImpl
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionInternal
import net.corda.sandbox.type.SandboxConstants.CORDA_UNINJECTABLE_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.serialization.checkpoint.CheckpointInput
import net.corda.serialization.checkpoint.CheckpointInternalCustomSerializer
import net.corda.serialization.checkpoint.CheckpointOutput
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [ CheckpointInternalCustomSerializer::class, UsedByFlow::class ],
    property = [ CORDA_UNINJECTABLE_SERVICE ],
    scope = PROTOTYPE
)
class ConsensualSignedTransactionKryoSerializer @Activate constructor(
    @Reference(service = SerializationService::class)
    private val serialisationService: SerializationService,
    @Reference(service = TransactionSignatureService::class)
    private val transactionSignatureService: TransactionSignatureService
) : CheckpointInternalCustomSerializer<ConsensualSignedTransactionInternal>, UsedByFlow {
    override val type: Class<ConsensualSignedTransactionInternal> get() = ConsensualSignedTransactionInternal::class.java

    override fun write(output: CheckpointOutput, obj: ConsensualSignedTransactionInternal) {
        output.writeClassAndObject(obj.wireTransaction)
        output.writeClassAndObject(obj.signatures)
    }

    override fun read(input: CheckpointInput, type: Class<out ConsensualSignedTransactionInternal>): ConsensualSignedTransactionInternal {
        val wireTransaction = input.readClassAndObject() as WireTransaction
        @Suppress("unchecked_cast")
        val signatures = input.readClassAndObject() as List<DigitalSignatureAndMetadata>
        return ConsensualSignedTransactionImpl(
            serialisationService,
            transactionSignatureService,
            wireTransaction,
            signatures
        )
    }
}
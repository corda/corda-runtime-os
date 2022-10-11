package net.corda.ledger.consensual.impl.transaction.serializer

import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.ledger.consensual.impl.transaction.ConsensualSignedTransactionImpl
import net.corda.serialization.checkpoint.CheckpointInput
import net.corda.serialization.checkpoint.CheckpointInternalCustomSerializer
import net.corda.serialization.checkpoint.CheckpointOutput
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.util.uncheckedCast
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [CheckpointInternalCustomSerializer::class])
class ConsensualSignedTransactionImplKryoSerializer @Activate constructor(
    @Reference(service = SerializationService::class) private val serialisationService: SerializationService,
    @Reference(service = SigningService::class) private val signingService: SigningService
) : CheckpointInternalCustomSerializer<ConsensualSignedTransactionImpl> {
    override val type: Class<ConsensualSignedTransactionImpl> get() = ConsensualSignedTransactionImpl::class.java

    override fun write(output: CheckpointOutput, obj: ConsensualSignedTransactionImpl) {
        output.writeClassAndObject(obj.wireTransaction)
        output.writeClassAndObject(obj.signatures)
    }

    override fun read(input: CheckpointInput, type: Class<ConsensualSignedTransactionImpl>): ConsensualSignedTransactionImpl {
        val wireTransaction = input.readClassAndObject() as WireTransaction
        val signatures: List<DigitalSignatureAndMetadata> = uncheckedCast(input.readClassAndObject())
        return ConsensualSignedTransactionImpl(
            serialisationService,
            signingService,
            wireTransaction,
            signatures
        )
    }
}
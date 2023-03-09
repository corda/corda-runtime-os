package net.corda.ledger.utxo.flow.impl.transaction.serializer.kryo

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.v5.ledger.common.transaction.TransactionSignatureService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionImpl
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoLedgerTransactionFactory
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
class UtxoSignedTransactionKryoSerializer @Activate constructor(
    @Reference(service = SerializationService::class)
    private val serialisationService: SerializationService,
    @Reference(service = TransactionSignatureService::class)
    private val transactionSignatureService: TransactionSignatureService,
    @Reference(service = UtxoLedgerTransactionFactory::class)
    private val utxoLedgerTransactionFactory: UtxoLedgerTransactionFactory
) : CheckpointInternalCustomSerializer<UtxoSignedTransactionInternal>, UsedByFlow {
    override val type: Class<UtxoSignedTransactionInternal> get() = UtxoSignedTransactionInternal::class.java

    override fun write(output: CheckpointOutput, obj: UtxoSignedTransactionInternal) {
        output.writeClassAndObject(obj.wireTransaction)
        output.writeClassAndObject(obj.signatures)
    }

    override fun read(input: CheckpointInput, type: Class<out UtxoSignedTransactionInternal>): UtxoSignedTransactionInternal {
        val wireTransaction = input.readClassAndObject() as WireTransaction
        @Suppress("unchecked_cast")
        val signatures = input.readClassAndObject() as List<DigitalSignatureAndMetadata>
        return UtxoSignedTransactionImpl(
            serialisationService,
            transactionSignatureService,
            utxoLedgerTransactionFactory,
            wireTransaction,
            signatures
        )
    }
}

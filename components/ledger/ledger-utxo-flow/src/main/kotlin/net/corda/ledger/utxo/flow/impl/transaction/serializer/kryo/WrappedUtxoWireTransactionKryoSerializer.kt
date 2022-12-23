package net.corda.ledger.utxo.flow.impl.transaction.serializer.kryo

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.utxo.data.transaction.WrappedUtxoWireTransaction
import net.corda.sandbox.type.SandboxConstants.CORDA_UNINJECTABLE_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.serialization.checkpoint.CheckpointInput
import net.corda.serialization.checkpoint.CheckpointInternalCustomSerializer
import net.corda.serialization.checkpoint.CheckpointOutput
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
class WrappedUtxoWireTransactionKryoSerializer @Activate constructor(
    @Reference(service = SerializationService::class)
    private val serialisationService: SerializationService
) : CheckpointInternalCustomSerializer<WrappedUtxoWireTransaction>, UsedByFlow {
    override val type: Class<WrappedUtxoWireTransaction> get() = WrappedUtxoWireTransaction::class.java

    override fun write(output: CheckpointOutput, obj: WrappedUtxoWireTransaction) {
        output.writeClassAndObject(obj.wireTransaction)
    }

    override fun read(input: CheckpointInput, type: Class<WrappedUtxoWireTransaction>): WrappedUtxoWireTransaction {
        val wireTransaction = input.readClassAndObject() as WireTransaction
        return WrappedUtxoWireTransaction(
            wireTransaction,
            serialisationService,
        )
    }
}
package net.corda.ledger.utxo.flow.impl.flows.finality.serializer.kryo

import net.corda.ledger.utxo.flow.impl.flows.finality.FinalityPayload
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.sandbox.type.SandboxConstants.CORDA_UNINJECTABLE_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByVerification
import net.corda.serialization.checkpoint.CheckpointInput
import net.corda.serialization.checkpoint.CheckpointInternalCustomSerializer
import net.corda.serialization.checkpoint.CheckpointOutput
import net.corda.v5.application.serialization.SerializationService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [CheckpointInternalCustomSerializer::class, UsedByFlow::class, UsedByVerification::class],
    property = [CORDA_UNINJECTABLE_SERVICE],
    scope = PROTOTYPE
)
class FinalityPayloadKryoSerializer @Activate constructor(
    @Reference(service = SerializationService::class)
    private val serialisationService: SerializationService,
) : CheckpointInternalCustomSerializer<FinalityPayload>, UsedByFlow, UsedByVerification {
    override val type: Class<FinalityPayload> get() = FinalityPayload::class.java

    override fun write(output: CheckpointOutput, obj: FinalityPayload) {
        output.writeClassAndObject(obj.transferAdditionalSignatures)
        output.writeClassAndObject(obj.initialTransaction)
    }

    override fun read(input: CheckpointInput, type: Class<out FinalityPayload>): FinalityPayload {
        return FinalityPayload(
            input.readClassAndObject() as UtxoSignedTransactionInternal,
            input.readClassAndObject() as Boolean,
            serialisationService
        )
    }
}
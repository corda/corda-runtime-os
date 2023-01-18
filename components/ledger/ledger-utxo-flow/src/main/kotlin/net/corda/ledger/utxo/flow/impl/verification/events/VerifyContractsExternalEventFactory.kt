package net.corda.ledger.utxo.flow.impl.verification.events

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.state.FlowCheckpoint
import net.corda.ledger.utxo.contract.verification.CordaPackageSummary
import net.corda.ledger.utxo.contract.verification.VerifyContractsRequest
import net.corda.schema.Schemas
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.nio.ByteBuffer
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class VerifyContractsExternalEventFactory(
    private val clock: Clock
) : ExternalEventFactory<VerifyContractsParameters, Boolean, Boolean>
{
    @Activate
    constructor() : this(Clock.systemUTC())

    override val responseType = Boolean::class.java

    override fun createExternalEvent(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: VerifyContractsParameters
    ): ExternalEventRecord {
        return ExternalEventRecord(
            topic = Schemas.Verification.VERIFICATION_LEDGER_PROCESSOR_TOPIC,
            payload = VerifyContractsRequest.newBuilder()
                .setTimestamp(clock.instant())
                .setHoldingIdentity(checkpoint.holdingIdentity.toAvro())
                .setTransaction(parameters.transaction)
                .setCpkMetadata(parameters.cpkMetadata)
                .setFlowExternalEventContext(flowExternalEventContext)
                .build()
        )
    }

    override fun resumeWith(checkpoint: FlowCheckpoint, response: Boolean): Boolean {
        return response
    }
}

data class VerifyContractsParameters(
    val transaction: ByteBuffer,
    val cpkMetadata: List<CordaPackageSummary>
)
package net.corda.ledger.utxo.flow.impl.transaction.verifier.external.events

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.state.FlowCheckpoint
import net.corda.ledger.utxo.data.transaction.TransactionVerificationResult
import net.corda.ledger.utxo.data.transaction.TransactionVerificationStatus
import net.corda.schema.Schemas
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.nio.ByteBuffer
import java.time.Clock
import net.corda.ledger.utxo.verification.CordaPackageSummary as CordaPackageSummaryAvro
import net.corda.ledger.utxo.verification.TransactionVerificationStatus as TransactionVerificationStatusAvro
import net.corda.ledger.utxo.verification.TransactionVerificationRequest as TransactionVerificationRequestAvro
import net.corda.ledger.utxo.verification.TransactionVerificationResponse as TransactionVerificationResponseAvro

@Component(service = [ExternalEventFactory::class])
class TransactionVerificationExternalEventFactory(
    private val clock: Clock
) : ExternalEventFactory<TransactionVerificationParameters, TransactionVerificationResponseAvro, TransactionVerificationResult>
{
    @Activate
    constructor() : this(Clock.systemUTC())

    override val responseType = TransactionVerificationResponseAvro::class.java

    override fun createExternalEvent(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: TransactionVerificationParameters
    ): ExternalEventRecord {
        return ExternalEventRecord(
            topic = Schemas.Verification.VERIFICATION_LEDGER_PROCESSOR_TOPIC,
            payload = TransactionVerificationRequestAvro.newBuilder()
                .setTimestamp(clock.instant())
                .setHoldingIdentity(checkpoint.holdingIdentity.toAvro())
                .setTransaction(parameters.transaction)
                .setCpkMetadata(parameters.cpkMetadata)
                .setFlowExternalEventContext(flowExternalEventContext)
                .build()
        )
    }

    override fun resumeWith(checkpoint: FlowCheckpoint, response: TransactionVerificationResponseAvro): TransactionVerificationResult {
        return response.fromAvro()
    }

    private fun TransactionVerificationResponseAvro.fromAvro() =
        TransactionVerificationResult(
            verificationStatus.fromAvro(),
            verificationFailure.errorType,
            verificationFailure.errorMessage
        )

    private fun TransactionVerificationStatusAvro.fromAvro() = when(this) {
        TransactionVerificationStatusAvro.INVALID -> TransactionVerificationStatus.INVALID
        TransactionVerificationStatusAvro.VERIFIED -> TransactionVerificationStatus.VERIFIED
    }
}

data class TransactionVerificationParameters(
    val transaction: ByteBuffer,
    val cpkMetadata: List<CordaPackageSummaryAvro>
)
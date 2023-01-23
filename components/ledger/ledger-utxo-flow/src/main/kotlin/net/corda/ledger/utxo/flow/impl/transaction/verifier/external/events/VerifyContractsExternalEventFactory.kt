package net.corda.ledger.utxo.flow.impl.transaction.verifier.external.events

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.state.FlowCheckpoint
import net.corda.ledger.utxo.data.transaction.ContractVerificationFailureImpl
import net.corda.ledger.utxo.data.transaction.ContractVerificationResult
import net.corda.ledger.utxo.data.transaction.ContractVerificationStatus
import net.corda.schema.Schemas
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.nio.ByteBuffer
import java.time.Clock
import net.corda.ledger.utxo.contract.verification.CordaPackageSummary as CordaPackageSummaryAvro
import net.corda.ledger.utxo.contract.verification.VerificationResult as VerificationResultAvro
import net.corda.ledger.utxo.contract.verification.VerifyContractsRequest as VerifyContractsRequestAvro
import net.corda.ledger.utxo.contract.verification.VerifyContractsResponse as VerifyContractsResponseAvro

@Component(service = [ExternalEventFactory::class])
class VerifyContractsExternalEventFactory(
    private val clock: Clock
) : ExternalEventFactory<VerifyContractsParameters, VerifyContractsResponseAvro, ContractVerificationResult>
{
    @Activate
    constructor() : this(Clock.systemUTC())

    override val responseType = VerifyContractsResponseAvro::class.java

    override fun createExternalEvent(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: VerifyContractsParameters
    ): ExternalEventRecord {
        return ExternalEventRecord(
            topic = Schemas.Verification.VERIFICATION_LEDGER_PROCESSOR_TOPIC,
            payload = VerifyContractsRequestAvro.newBuilder()
                .setTimestamp(clock.instant())
                .setHoldingIdentity(checkpoint.holdingIdentity.toAvro())
                .setTransaction(parameters.transaction)
                .setCpkMetadata(parameters.cpkMetadata)
                .setFlowExternalEventContext(flowExternalEventContext)
                .build()
        )
    }

    override fun resumeWith(checkpoint: FlowCheckpoint, response: VerifyContractsResponseAvro): ContractVerificationResult {
        return response.fromAvro()
    }

    private fun VerifyContractsResponseAvro.fromAvro() =
        ContractVerificationResult(
            result.fromAvro(),
            verificationFailures.map{ it.fromAvro() }
        )

    private fun VerificationResultAvro.fromAvro() = when(this) {
        VerificationResultAvro.INVALID -> ContractVerificationStatus.INVALID
        VerificationResultAvro.VERIFIED -> ContractVerificationStatus.VERIFIED
    }

    private fun net.corda.ledger.utxo.contract.verification.ContractVerificationFailure.fromAvro() =
        ContractVerificationFailureImpl(
            contractClassName,
            contractStateClassNames,
            exceptionClassName,
            exceptionMessage
        )
}

data class VerifyContractsParameters(
    val transaction: ByteBuffer,
    val cpkMetadata: List<CordaPackageSummaryAvro>
)
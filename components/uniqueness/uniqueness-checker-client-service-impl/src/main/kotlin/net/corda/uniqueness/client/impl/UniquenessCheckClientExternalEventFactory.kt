package net.corda.uniqueness.client.impl

import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.data.uniqueness.UniquenessCheckResponseAvro
import net.corda.data.uniqueness.UniquenessCheckResultInputStateConflictAvro
import net.corda.data.uniqueness.UniquenessCheckResultInputStateUnknownAvro
import net.corda.data.uniqueness.UniquenessCheckResultMalformedRequestAvro
import net.corda.data.uniqueness.UniquenessCheckResultNotPreviouslySeenTransactionAvro
import net.corda.data.uniqueness.UniquenessCheckResultReferenceStateConflictAvro
import net.corda.data.uniqueness.UniquenessCheckResultReferenceStateUnknownAvro
import net.corda.data.uniqueness.UniquenessCheckResultSuccessAvro
import net.corda.data.uniqueness.UniquenessCheckResultTimeWindowBeforeLowerBoundAvro
import net.corda.data.uniqueness.UniquenessCheckResultTimeWindowOutOfBoundsAvro
import net.corda.data.uniqueness.UniquenessCheckResultUnhandledExceptionAvro
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.state.FlowCheckpoint
import net.corda.uniqueness.datamodel.common.toStateRef
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorInputStateConflictImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorInputStateUnknownImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorMalformedRequestImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorNotPreviouslySeenTransactionImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorReferenceStateConflictImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorReferenceStateUnknownImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorTimeWindowBeforeLowerBoundImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorTimeWindowOutOfBoundsImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorUnhandledExceptionImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultFailureImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultSuccessImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckStateDetailsImpl
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Component
import java.time.Instant
import net.corda.flow.external.events.ExternalEventContext
import net.corda.flow.utils.toAvro
import net.corda.data.uniqueness.UniquenessCheckType as UniquenessCheckTypeAvro

@Component(service = [ExternalEventFactory::class])
class UniquenessCheckExternalEventFactory :
    ExternalEventFactory<UniquenessCheckExternalEventParams, UniquenessCheckResponseAvro, UniquenessCheckResult> {

    override val responseType = UniquenessCheckResponseAvro::class.java

    override fun createExternalEvent(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: UniquenessCheckExternalEventParams
    ): ExternalEventRecord {
        return ExternalEventRecord(
            payload = createRequest(parameters, flowExternalEventContext, checkpoint)
        )
    }

    override fun resumeWith(checkpoint: FlowCheckpoint, response: UniquenessCheckResponseAvro): UniquenessCheckResult {
        return response.toUniquenessResult()
    }

    private fun createRequest(
        params: UniquenessCheckExternalEventParams,
        context: ExternalEventContext,
        checkpoint: FlowCheckpoint
    ) = UniquenessCheckRequestAvro.newBuilder()
        .setHoldingIdentity(checkpoint.holdingIdentity.toAvro())
        .setFlowExternalEventContext(context.toAvro())
        .setTxId(params.txId)
        .setOriginatorX500Name(params.originatorX500Name)
        .setInputStates(params.inputStates)
        .setReferenceStates(params.referenceStates)
        .setNumOutputStates(params.numOutputStates)
        .setTimeWindowLowerBound(params.timeWindowLowerBound)
        .setTimeWindowUpperBound(params.timeWindowUpperBound)
        .setUniquenessCheckType(params.uniquenessCheckType.toAvro())
        .build()
}

@CordaSerializable
data class UniquenessCheckExternalEventParams(
    val txId: String,
    val originatorX500Name: String,
    val inputStates: List<String>,
    val referenceStates: List<String>,
    val numOutputStates: Int,
    val timeWindowLowerBound: Instant?,
    val timeWindowUpperBound: Instant,
    val uniquenessCheckType: UniquenessCheckType
)

@CordaSerializable
enum class UniquenessCheckType {
    WRITE, READ;

    fun toAvro(): UniquenessCheckTypeAvro {
        return when (this) {
            WRITE -> UniquenessCheckTypeAvro.WRITE
            READ -> UniquenessCheckTypeAvro.READ
        }
    }
}

/**
 * Converts an Avro result to a [UniquenessCheckResult].
 */
fun UniquenessCheckResponseAvro.toUniquenessResult(): UniquenessCheckResult {

    return when (val avroResult = result) {
        is UniquenessCheckResultInputStateConflictAvro -> {
            UniquenessCheckResultFailureImpl(
                Instant.now(),
                UniquenessCheckErrorInputStateConflictImpl(avroResult.conflictingStates.map {
                    // FIXME Consuming tx hash is populated as [null] for now
                    UniquenessCheckStateDetailsImpl(it.toStateRef(), null)
                })
            )
        }
        is UniquenessCheckResultInputStateUnknownAvro -> {
            UniquenessCheckResultFailureImpl(
                Instant.now(),
                UniquenessCheckErrorInputStateUnknownImpl(avroResult.unknownStates.map {
                    it.toStateRef()
                })
            )
        }
        is UniquenessCheckResultReferenceStateConflictAvro -> {
            UniquenessCheckResultFailureImpl(
                Instant.now(),
                UniquenessCheckErrorReferenceStateConflictImpl(avroResult.conflictingStates.map {
                    // FIXME Consuming tx hash is populated as [null] for now
                    UniquenessCheckStateDetailsImpl(it.toStateRef(), null)
                })
            )
        }
        is UniquenessCheckResultReferenceStateUnknownAvro -> {
            UniquenessCheckResultFailureImpl(
                Instant.now(),
                UniquenessCheckErrorReferenceStateUnknownImpl(avroResult.unknownStates.map {
                    it.toStateRef()
                })
            )
        }
        is UniquenessCheckResultTimeWindowOutOfBoundsAvro -> {
            UniquenessCheckResultFailureImpl(
                Instant.now(),
                UniquenessCheckErrorTimeWindowOutOfBoundsImpl(
                    avroResult.evaluationTimestamp,
                    avroResult.timeWindowLowerBound,
                    avroResult.timeWindowUpperBound
                )
            )
        }
        is UniquenessCheckResultTimeWindowBeforeLowerBoundAvro -> {
            UniquenessCheckResultFailureImpl(
                Instant.now(),
                UniquenessCheckErrorTimeWindowBeforeLowerBoundImpl(
                    avroResult.evaluationTimestamp,
                    avroResult.timeWindowLowerBound,
                )
            )
        }
        is UniquenessCheckResultMalformedRequestAvro -> {
            UniquenessCheckResultFailureImpl(
                Instant.now(),
                UniquenessCheckErrorMalformedRequestImpl(
                    avroResult.errorText
                )
            )
        }
        is UniquenessCheckResultUnhandledExceptionAvro -> {
            UniquenessCheckResultFailureImpl(
                Instant.now(),
                UniquenessCheckErrorUnhandledExceptionImpl(
                    avroResult.exception.errorType,
                    avroResult.exception.errorMessage
                )
            )
        }
        is UniquenessCheckResultNotPreviouslySeenTransactionAvro -> {
            UniquenessCheckResultFailureImpl(
                Instant.now(),
                UniquenessCheckErrorNotPreviouslySeenTransactionImpl
            )
        }
        is UniquenessCheckResultSuccessAvro -> {
            UniquenessCheckResultSuccessImpl(avroResult.commitTimestamp)
        }
        else -> {
            throw IllegalArgumentException(
                "Unable to convert Avro type \"${avroResult.javaClass.typeName}\" to result"
            )
        }
    }
}
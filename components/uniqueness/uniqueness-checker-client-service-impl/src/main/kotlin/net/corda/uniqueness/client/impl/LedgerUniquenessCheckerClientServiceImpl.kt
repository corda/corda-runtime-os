package net.corda.uniqueness.client.impl

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.metrics.CordaMetrics
import net.corda.sandbox.type.UsedByFlow
import net.corda.utilities.debug
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultFailure
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.uniqueness.client.LedgerUniquenessCheckerClientService
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

/**
 * Implementation of the Uniqueness Checker Client Service which will invoke the batched uniqueness checker
 * through the message bus. This communication uses the external events API. Once the uniqueness checker has
 * finished the validation of the given batch it will return the response to the client service.
 */
@Component(service = [ LedgerUniquenessCheckerClientService::class, UsedByFlow::class ], scope = PROTOTYPE)
class LedgerUniquenessCheckerClientServiceImpl @Activate constructor(
    @Reference(service = ExternalEventExecutor::class)
    private val externalEventExecutor: ExternalEventExecutor
): LedgerUniquenessCheckerClientService, UsedByFlow, SingletonSerializeAsToken {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @Suspendable
    override fun requestUniquenessCheck(
        transactionId: String,
        originatorX500Name: String,
        inputStates: List<String>,
        referenceStates: List<String>,
        numOutputStates: Int,
        timeWindowLowerBound: Instant?,
        timeWindowUpperBound: Instant
    ): UniquenessCheckResult {
        log.debug { "Received request with id: $transactionId, sending it to Uniqueness Checker" }

        val startTime = System.nanoTime()

        return externalEventExecutor.execute(
            UniquenessCheckExternalEventFactory::class.java,
            UniquenessCheckExternalEventParams(
                transactionId,
                originatorX500Name,
                inputStates,
                referenceStates,
                numOutputStates,
                timeWindowLowerBound,
                timeWindowUpperBound,
                UniquenessCheckType.NOTARIZE
            )
        ).also {
            CordaMetrics.Metric.Ledger.UniquenessClientRunTime
                .builder()
                .withTag(
                    CordaMetrics.Tag.ResultType,
                    if (UniquenessCheckResultFailure::class.java.isAssignableFrom(it.javaClass)) {
                        (it as UniquenessCheckResultFailure).error.javaClass.simpleName
                    } else {
                        it.javaClass.simpleName
                    })
                .build()
                .record(Duration.ofNanos(System.nanoTime() - startTime))
        }
    }

    @Suspendable
    override fun requestUniquenessCheck(
        transactionId: String,
        originatorX500Name: String,
        timeWindowLowerBound: Instant?,
        timeWindowUpperBound: Instant
    ): UniquenessCheckResult {
        log.debug { "Received check request with id: $transactionId, sending it to Uniqueness Checker" }

        val startTime = System.nanoTime()

        return externalEventExecutor.execute(
            UniquenessCheckExternalEventFactory::class.java,
            UniquenessCheckExternalEventParams(
                transactionId,
                originatorX500Name,
                emptyList(),
                emptyList(),
                0,
                timeWindowLowerBound,
                timeWindowUpperBound,
                UniquenessCheckType.CHECK
            )
        ).also {
            CordaMetrics.Metric.Ledger.UniquenessClientRunTime
                .builder()
                .withTag(
                    CordaMetrics.Tag.ResultType,
                    if (UniquenessCheckResultFailure::class.java.isAssignableFrom(it.javaClass)) {
                        (it as UniquenessCheckResultFailure).error.javaClass.simpleName
                    } else {
                        it.javaClass.simpleName
                    })
                .build()
                .record(Duration.ofNanos(System.nanoTime() - startTime))
        }
    }
}

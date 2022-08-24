package net.corda.uniqueness.checker.impl

import net.corda.data.uniqueness.UniquenessCheckExternalRequest
import net.corda.data.uniqueness.UniquenessCheckExternalResponse
import net.corda.data.uniqueness.UniquenessCheckExternalResultMalformedRequest
import net.corda.data.uniqueness.UniquenessCheckExternalResultSuccess
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.uniqueness.backingstore.BackingStore
import net.corda.uniqueness.checker.UniquenessChecker
import net.corda.uniqueness.common.datamodel.UniquenessCheckInternalRequest
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.application.uniqueness.model.UniquenessCheckError
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateDetails
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import net.corda.v5.application.uniqueness.model.UniquenessCheckTransactionDetails
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SecureHash
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.time.Instant
import java.util.*
import kotlin.collections.HashMap

/**
 * A batched implementation of the uniqueness checker component, which processes batches of requests
 * together in order to provide higher performance under load.
 */
@Component(service = [UniquenessChecker::class])
class BatchedUniquenessCheckerImpl(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val clock: Clock,
    private val backingStore: BackingStore
) : UniquenessChecker {

    @Activate
    constructor(
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
        @Reference(service = BackingStore::class)
        backingStore: BackingStore
    ) : this(coordinatorFactory, UTCClock(), backingStore)

    private companion object {
        private val log: Logger = contextLogger()
    }

    private val lifecycleCoordinator: LifecycleCoordinator =
        coordinatorFactory.createCoordinator<UniquenessChecker>(::eventHandler)

    private val dependentComponents = DependentComponents.of(
        ::backingStore
    )

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    // In-memory caches of transaction and state details. When processing a batch, these are
    // initially seeded from the backing store, but are updated as we iterate through a batch
    private val transactionDetailsCache =
        HashMap<SecureHash, UniquenessCheckTransactionDetails>()
    private val stateDetailsCache =
        HashMap<UniquenessCheckStateRef, UniquenessCheckStateDetails>()

    override fun start() {
        log.info("Uniqueness checker starting.")
        lifecycleCoordinator.start()
    }

    override fun stop() {
        log.info("Uniqueness checker stopping.")
        lifecycleCoordinator.stop()
    }

    @Synchronized
    override fun processRequests(
        requests: List<UniquenessCheckExternalRequest>
    ): List<UniquenessCheckExternalResponse> {

        val results = LinkedList<UniquenessCheckExternalResponse>()

        // Convert the supplied batch of external requests to internal requests. Doing this can
        // throw an exception if the request is malformed. These are filtered out immediately with
        // the appropriate result returned as we can't make any assumptions about the input and
        // such a failure would be inherently idempotent anyway (changing the request would change
        // the tx id)
        val requestsToProcess = requests.mapNotNull {
            try {
                UniquenessCheckInternalRequest.create(it)
            } catch (e: IllegalArgumentException) {
                results.add(
                    UniquenessCheckExternalResponse(
                        it.txId,
                        UniquenessCheckExternalResultMalformedRequest(e.message)
                    )
                )
                null
            }
        }

        // TODO - Re-instate batch processing logic if needed - need to establish what batching
        // there is in the message bus layer first
        results += processBatch(requestsToProcess).map { (request, result) ->
            UniquenessCheckExternalResponse(
                request.rawTxId,
                when (result) {
                    is UniquenessCheckResult.Success -> {
                        UniquenessCheckExternalResultSuccess(result.commitTimestamp)
                    }
                    is UniquenessCheckResult.Failure -> {
                        result.toExternalError()
                    }
                }
            )
        }

        return results
    }

    @Suppress("ComplexMethod", "LongMethod")
    private fun processBatch(
        batch: List<UniquenessCheckInternalRequest>
    ): List<Pair<UniquenessCheckInternalRequest, UniquenessCheckResult>> {

        val resultsToRespondWith =
            mutableListOf<Pair<UniquenessCheckInternalRequest, UniquenessCheckResult>>()

        // DB operations are retried, removing conflicts from the batch on each attempt.
        backingStore.transactionSession { session, transactionOps ->
            // We can clear these between retries, data will be retrieved from the backing store
            // anyway.
            stateDetailsCache.clear()
            transactionDetailsCache.clear()

            resultsToRespondWith.clear()

            // 1. Load our caches with the data persisted in the backing store
            transactionDetailsCache.putAll(
                session.getTransactionDetails(batch.map { it.txId })
            )
            stateDetailsCache.putAll(
                session.getStateDetails(batch.flatMap { it.inputStates + it.referenceStates })
            )

            // 2. Process requests one by one and run checks on them. The resultsToCommit is a
            //    subset of resultsToRespondWith and reflects only those results that need to be
            //    written to the backing store
            val resultsToCommit = LinkedList<Pair<
                    UniquenessCheckInternalRequest, UniquenessCheckResult>>()

            batch.forEach { request ->
                // Already processed -> Return same result as in DB (idempotency) but no need to
                // commit to backing store so is not added to resultsToCommit
                if (transactionDetailsCache[request.txId] != null) {
                    resultsToRespondWith.add(
                        Pair(request, transactionDetailsCache[request.txId]!!.result)
                    )
                } else {
                    val (knownInputStates, unknownInputStates) =
                        request.inputStates.partition { stateDetailsCache.contains(it) }
                    val (knownReferenceStates, unknownReferenceStates) =
                        request.referenceStates.partition { stateDetailsCache.contains(it) }
                    val inputStateConflicts = knownInputStates.filter {
                        with(stateDetailsCache[it]!!) {
                            consumingTxId != null && consumingTxId != request.txId
                        }
                    }
                    val referenceStateConflicts = knownReferenceStates.filter {
                        with(stateDetailsCache[it]!!) {
                            consumingTxId != null && consumingTxId != request.txId
                        }
                    }
                    val timeWindowEvaluationTime = clock.instant()

                    val result = when {
                        // Unknown input state -> Immediate failure
                        unknownInputStates.isNotEmpty() ->
                            handleRejectedRequest(
                                request,
                                UniquenessCheckError
                                    .InputStateUnknown(unknownInputStates)
                            )
                        // Unknown reference state -> Immediate failure
                        unknownReferenceStates.isNotEmpty() ->
                            handleRejectedRequest(
                                request,
                                UniquenessCheckError
                                    .ReferenceStateUnknown(unknownReferenceStates)
                            )
                        // Input state conflict check
                        inputStateConflicts.isNotEmpty() ->
                            handleRejectedRequest(
                                request,
                                UniquenessCheckError.InputStateConflict(
                                    inputStateConflicts.map { stateDetailsCache[it]!! }
                                )
                            )
                        // Reference state conflict check
                        referenceStateConflicts.isNotEmpty() ->
                            handleRejectedRequest(
                                request,
                                UniquenessCheckError.ReferenceStateConflict(
                                    referenceStateConflicts.map { stateDetailsCache[it]!! }
                                )
                            )
                        // Time window check
                        !isTimeWindowValid(
                            timeWindowEvaluationTime,
                            request.timeWindowLowerBound,
                            request.timeWindowUpperBound
                        ) ->
                            handleRejectedRequest(
                                request,
                                UniquenessCheckError.TimeWindowOutOfBounds(
                                    timeWindowEvaluationTime,
                                    request.timeWindowLowerBound,
                                    request.timeWindowUpperBound
                                )
                            )
                        // All checks passed
                        else ->
                            handleSuccessfulRequest(request)
                    }

                    resultsToRespondWith.add(result)
                    resultsToCommit.add(result)
                }
            }

            // Now that the processing has finished, we need to commit to the database.
            commitResults(transactionOps, resultsToCommit)
        }

        return resultsToRespondWith
    }

    private fun handleRejectedRequest(
        request: UniquenessCheckInternalRequest,
        error: UniquenessCheckError
    ): Pair<UniquenessCheckInternalRequest, UniquenessCheckResult> {

        val rejectedResult = UniquenessCheckResult.Failure(clock.instant(), error)

        transactionDetailsCache[request.txId] = UniquenessCheckTransactionDetails(
            request.txId,
            rejectedResult
        )

        return Pair(request, rejectedResult)
    }

    private fun handleSuccessfulRequest(
        request: UniquenessCheckInternalRequest
    ): Pair<UniquenessCheckInternalRequest, UniquenessCheckResult> {

        val txDetails = UniquenessCheckTransactionDetails(
            request.txId,
            UniquenessCheckResult.Success(clock.instant())
        )

        transactionDetailsCache[request.txId] = txDetails

        request.inputStates.forEach {
            stateDetailsCache[it] = UniquenessCheckStateDetails(it, request.txId)
        }

        repeat(request.numOutputStates) {
            val outputStateRef = UniquenessCheckStateRef(request.txId, it)

            stateDetailsCache[outputStateRef] =
                UniquenessCheckStateDetails(outputStateRef, null)
        }

        return Pair(request, txDetails.result)
    }

    /**
     * A function to commit all the requests that were processed by the uniqueness service.
     *
     * If a request has failed it will be inserted to the TX and rejected TX tables.
     * If a request was successful it will be inserted to the TX table and its states
     * will either be inserted (output states) or updated (input states).
     *
     */
    private fun commitResults(
        txOps: BackingStore.Session.TransactionOps,
        results: List<Pair<UniquenessCheckInternalRequest, UniquenessCheckResult>>
    ) {
        txOps.commitTransactions(results)

        results.filter { it.second is UniquenessCheckResult.Success }.forEach { (request, _) ->

            // Insert the output states
            txOps.createUnconsumedStates(
                List(request.numOutputStates) {
                    UniquenessCheckStateRef(request.txId, it)
                }
            )

            // At this point it should be safe to update the states, we already checked for
            // double spends and unknown states and we have in-flight protection in our update
            txOps.consumeStates(request.txId, request.inputStates.map { it })
        }
    }

    private fun isTimeWindowValid(
        currentTime: Instant,
        timeWindowLowerBound: Instant?,
        timeWindowUpperBound: Instant
    ): Boolean {
        return (
            (timeWindowLowerBound == null || !timeWindowLowerBound.isAfter(currentTime)) &&
                timeWindowUpperBound.isAfter(currentTime)
            )
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.info("Uniqueness checker received event $event.")
        when (event) {
            is StartEvent -> {
                dependentComponents.registerAndStartAll(coordinator)
            }
            is StopEvent -> {
                dependentComponents.stopAll()
            }
            is RegistrationStatusChangeEvent -> {
                log.info("Uniqueness checker is ${event.status}")

                coordinator.updateStatus(event.status)
            }
            else -> {
                log.warn("Unexpected event $event!")
            }
        }
    }
}

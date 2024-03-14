package net.corda.uniqueness.checker.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.data.uniqueness.UniquenessCheckResponseAvro
import net.corda.data.uniqueness.UniquenessCheckResultMalformedRequestAvro
import net.corda.data.uniqueness.UniquenessCheckResultUnhandledExceptionAvro
import net.corda.metrics.CordaMetrics
import net.corda.uniqueness.backingstore.BackingStore
import net.corda.uniqueness.checker.UniquenessChecker
import net.corda.uniqueness.datamodel.common.toAvro
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorInputStateConflictImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorInputStateUnknownImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorReferenceStateConflictImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorReferenceStateUnknownImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorTimeWindowOutOfBoundsImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorUnhandledExceptionImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultFailureImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultSuccessImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckStateDetailsImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckStateRefImpl
import net.corda.uniqueness.datamodel.internal.UniquenessCheckRequestInternal
import net.corda.uniqueness.datamodel.internal.UniquenessCheckTransactionDetailsInternal
import net.corda.utilities.debug
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.application.uniqueness.model.UniquenessCheckError
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultFailure
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultSuccess
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateDetails
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.LinkedList

/**
 * A batched implementation of the uniqueness checker component, which processes batches of requests
 * together in order to provide higher performance under load.
 */
@Component(service = [ UniquenessChecker::class ])
@Suppress("unused")
class BatchedUniquenessCheckerImpl(
    private val backingStore: BackingStore,
    private val clock: Clock
) : UniquenessChecker {

    @Activate
    constructor(
        @Reference(service = BackingStore::class)
        backingStore: BackingStore
    ) : this(backingStore, UTCClock())

    private companion object {
        private const val UNHANDLED_EXCEPTION = "UniquenessCheckResultUnhandledException"

        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    // Used to capture additional context associated with a result which is only relevant within
    // the scope of the uniqueness checker implementation
    private data class InternalUniquenessCheckResultWithContext(
        val result: UniquenessCheckResult,
        val isDuplicate: Boolean
    )

    // In-memory caches of transaction and state details. When processing a batch, these are
    // initially seeded from the backing store, but are updated as we iterate through a batch
    private val transactionDetailsCache =
        HashMap<SecureHash, UniquenessCheckTransactionDetailsInternal>()
    private val stateDetailsCache =
        HashMap<UniquenessCheckStateRef, UniquenessCheckStateDetails>()

    /**
     * Performs uniqueness checking against a list of requests and returns a map of requests and
     * their corresponding responses. This implementation will process valid (i.e. non-malformed)
     * requests in the order they are presented in the [requests] list for a given holding identity,
     * but no guarantees are given for the ordering of requests across different holding identities.
     *
     * The ordering of the returned mappings is not guaranteed to match those of the supplied
     * [requests] parameter. Callers should therefore use the request objects in the returned
     * responses if relying on any data stored in the request.
     *
     * See [UniquenessCheckRequestAvro] and [UniquenessCheckResponseAvro] for details of message
     * formats.
     */
    @Synchronized
    override fun processRequests(
        requests: List<UniquenessCheckRequestAvro>
    ): Map<UniquenessCheckRequestAvro, UniquenessCheckResponseAvro> {

        val batchStartTime = System.nanoTime()
        val results = HashMap<UniquenessCheckRequestAvro, UniquenessCheckResponseAvro>()
        val requestsToProcess = ArrayList<
                Pair<UniquenessCheckRequestInternal, UniquenessCheckRequestAvro>>(requests.size)

        log.debug { "Processing ${requests.size} requests" }

        // Convert the supplied batch of external requests to internal requests. Doing this can
        // throw an exception if the request is malformed. These are filtered out immediately with
        // the appropriate result returned as we can't make any assumptions about the input and
        // such a failure would be inherently idempotent anyway (changing the request would change
        // the tx id)
        var numMalformed = 0

        // TODO CORE-7250 We have no way to pre-check the number of reference states in the plugin
        //  server anymore, so we need to make sure that not having the 10k limit for reference states is,
        //  okay, if not, we need to re-add the check in this class.

        for ( request in requests ) {
            try {
                requestsToProcess.add(
                    Pair(UniquenessCheckRequestInternal.create(request), request))
            } catch (e: IllegalArgumentException) {
                results[request] = UniquenessCheckResponseAvro(
                    request.txId,
                    UniquenessCheckResultMalformedRequestAvro(e.message)
                )
                ++numMalformed
            }
        }

        if ( numMalformed > 0 ) { log.debug { "$numMalformed malformed requests were rejected" } }

        val (checks, notarizations) = requestsToProcess.partition { (request, _) ->
            request.numOutputStates == 0 && request.inputStates.isEmpty() && request.referenceStates.isEmpty()
        }

        // TODO - Re-instate batch processing logic based on number of states if needed - need to
        // establish what batching there is in the message bus layer first
        processBatches(notarizations, results, ::processUniquenessCheckBatch)
        processBatches(checks, results, ::processExistingUniquenessCheckBatch)

        CordaMetrics.Metric.UniquenessCheckerBatchExecutionTime
            .builder()
            .build()
            .record(Duration.ofNanos(System.nanoTime() - batchStartTime))

        CordaMetrics.Metric.UniquenessCheckerBatchSize
            .builder()
            .build()
            .record(requests.size.toDouble())

        return results
    }

    private inline fun processBatches(
        requestsToProcess: List<Pair<UniquenessCheckRequestInternal, UniquenessCheckRequestAvro>>,
        results: HashMap<UniquenessCheckRequestAvro, UniquenessCheckResponseAvro>,
        processingCallback: (holdingIdentity: HoldingIdentity, batch: List<UniquenessCheckRequestInternal>)
            -> List<Pair<UniquenessCheckRequestInternal, InternalUniquenessCheckResultWithContext>>
    ) {
        requestsToProcess
            // Partition the data based on holding identity, as each should be processed separately
            // so the requests cannot interact with each other and the backing store also requires a
            // separate session per holding identity.
            .groupBy { it.second.holdingIdentity }
            // Converting to a list and then shuffling ensures a random order based on holding id to
            // avoid always prioritising one holding id over another when there is contention, e.g.
            // we don't want holding id 0xFFFF... to always be the last batch processed. Longer term
            // this should probably be replaced with a proper QoS algorithm.
            .toList()
            .shuffled()
            .forEach { (holdingIdentity, partitionedRequests) ->
                val subBatchStartTime = System.nanoTime()
                val cordaHoldingIdentity = holdingIdentity.toCorda()

                try {
                    processingCallback(
                        cordaHoldingIdentity,
                        partitionedRequests.map { it.first }
                    ).forEachIndexed { idx, (internalRequest, internalResult) ->
                        results[partitionedRequests[idx].second] = UniquenessCheckResponseAvro(
                            internalRequest.rawTxId, internalResult.result.toAvro())
                        CordaMetrics.Metric.UniquenessCheckerRequestCount
                            .builder()
                            .withTag(CordaMetrics.Tag.SourceVirtualNode, cordaHoldingIdentity.shortHash.toString())
                            .withTag(
                                CordaMetrics.Tag.ResultType,
                                if (UniquenessCheckResultFailure::class.java.isAssignableFrom(internalResult.result.javaClass)) {
                                    (internalResult.result as UniquenessCheckResultFailure).error.javaClass.simpleName
                                } else {
                                    internalResult.result.javaClass.simpleName
                                }
                            )
                            .withTag(CordaMetrics.Tag.IsDuplicate, internalResult.isDuplicate.toString())
                            .build()
                            .increment()
                    }
                } catch (e: Exception) {
                    // In practice, if we've received an unhandled exception then this will be before we
                    // managed to commit to the DB, so raise an exception against all requests in the
                    // batch
                    log.warn("Unhandled exception was thrown for transaction(s) " +
                            "${partitionedRequests.map { it.second.txId }}: $e")

                    partitionedRequests.forEachIndexed { idx, (internalRequest, _) ->
                        results[partitionedRequests[idx].second] = UniquenessCheckResponseAvro(
                            internalRequest.rawTxId,
                            UniquenessCheckResultUnhandledExceptionAvro(
                                ExceptionEnvelope().apply {
                                    errorType = e::class.java.name
                                    errorMessage = e.message
                                }
                            )
                        )

                        // IsDuplicate tag is omitted, as we do not have the information to deduce this in
                        // an unhandled exception scenario
                        CordaMetrics.Metric.UniquenessCheckerRequestCount
                            .builder()
                            .withTag(CordaMetrics.Tag.SourceVirtualNode, cordaHoldingIdentity.shortHash.toString())
                            .withTag(CordaMetrics.Tag.ResultType, UNHANDLED_EXCEPTION)
                            .withTag(CordaMetrics.Tag.ErrorType, e::class.java.simpleName)
                            .build()
                            .increment()
                    }
                }

                CordaMetrics.Metric.UniquenessCheckerSubBatchExecutionTime
                    .builder()
                    .withTag(CordaMetrics.Tag.SourceVirtualNode, cordaHoldingIdentity.shortHash.toString())
                    .build()
                    .record(Duration.ofNanos(System.nanoTime() - subBatchStartTime))

                CordaMetrics.Metric.UniquenessCheckerSubBatchSize
                    .builder()
                    .withTag(CordaMetrics.Tag.SourceVirtualNode, cordaHoldingIdentity.shortHash.toString())
                    .build()
                    .record(partitionedRequests.size.toDouble())
            }
    }

    @Suppress("ComplexMethod", "LongMethod")
    private fun processUniquenessCheckBatch(
        holdingIdentity: HoldingIdentity,
        batch: List<UniquenessCheckRequestInternal>
    ): List<Pair<UniquenessCheckRequestInternal, InternalUniquenessCheckResultWithContext>> {

        val resultsToRespondWith =
            mutableListOf<Pair<UniquenessCheckRequestInternal, InternalUniquenessCheckResultWithContext>>()

        log.debug { "Processing uniqueness batch of ${batch.size} requests for $holdingIdentity" }

        // DB operations are retried, removing conflicts from the batch on each attempt.
        backingStore.transactionSession(holdingIdentity) { session, transactionOps ->
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
                    UniquenessCheckRequestInternal, UniquenessCheckResult>>()

            batch.forEach { request ->
                // Already processed -> Return same result as in DB (idempotency) but no need to
                // commit to backing store so is not added to resultsToCommit
                if (transactionDetailsCache[request.txId] != null) {
                    resultsToRespondWith.add(
                        Pair(
                            request,
                            InternalUniquenessCheckResultWithContext(
                                transactionDetailsCache[request.txId]!!.result, isDuplicate = true)
                        )
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
                        unknownInputStates.isNotEmpty() -> {
                            log.info("Request for transaction ${request.txId} failed due to unknown " +
                                    "input states $unknownInputStates")
                            handleRejectedRequest(
                                request,
                                UniquenessCheckErrorInputStateUnknownImpl(unknownInputStates)
                            )
                        }
                        // Unknown reference state -> Immediate failure
                        unknownReferenceStates.isNotEmpty() -> {
                            log.info("Request for transaction ${request.txId} failed due to unknown " +
                                    "reference states $unknownReferenceStates")
                            handleRejectedRequest(
                                request,
                                UniquenessCheckErrorReferenceStateUnknownImpl(unknownReferenceStates)
                            )
                        }
                        // Input state conflict check
                        inputStateConflicts.isNotEmpty() -> {
                            log.info("Request for transaction ${request.txId} failed due to conflicting " +
                                    "input states $inputStateConflicts")
                            handleRejectedRequest(
                                request,
                                UniquenessCheckErrorInputStateConflictImpl(
                                    inputStateConflicts.map { stateDetailsCache[it]!! }
                                )
                            )
                        }
                        // Reference state conflict check
                        referenceStateConflicts.isNotEmpty() -> {
                            log.info("Request for transaction ${request.txId} failed due to conflicting " +
                                    "reference states $referenceStateConflicts")
                            handleRejectedRequest(
                                request,
                                UniquenessCheckErrorReferenceStateConflictImpl(
                                    referenceStateConflicts.map { stateDetailsCache[it]!! }
                                )
                            )
                        }
                        // Time window check
                        !isTimeWindowValid(
                            timeWindowEvaluationTime,
                            request.timeWindowLowerBound,
                            request.timeWindowUpperBound
                        ) ->
                            handleRejectedRequest(
                                request,
                                UniquenessCheckErrorTimeWindowOutOfBoundsImpl(
                                    timeWindowEvaluationTime,
                                    request.timeWindowLowerBound,
                                    request.timeWindowUpperBound
                                )
                            )
                        // All checks passed
                        else -> handleSuccessfulRequest(request)
                    }

                    resultsToRespondWith.add(Pair(
                        result.first,
                        InternalUniquenessCheckResultWithContext(result.second, isDuplicate = false))
                    )
                    resultsToCommit.add(result)
                }
            }

            // Now that the processing has finished, we need to commit to the database.
            commitResults(transactionOps, resultsToCommit)
        }

        if (log.isDebugEnabled) {
            val numSuccessful = resultsToRespondWith.filter {
                it.second.result is UniquenessCheckResultSuccess }.size

            log.debug { "Finished processing batch for $holdingIdentity. " +
                    "$numSuccessful successful, " +
                    "${resultsToRespondWith.size - numSuccessful} rejected" }
        }

        return resultsToRespondWith
    }

    @Suppress("ComplexMethod", "LongMethod")
    private fun processExistingUniquenessCheckBatch(
        holdingIdentity: HoldingIdentity,
        batch: List<UniquenessCheckRequestInternal>
    ): List<Pair<UniquenessCheckRequestInternal, InternalUniquenessCheckResultWithContext>> {

        val resultsToRespondWith =
            mutableListOf<Pair<UniquenessCheckRequestInternal, InternalUniquenessCheckResultWithContext>>()

        log.debug { "Processing existing uniqueness check batch of ${batch.size} requests for $holdingIdentity" }

        // DB operations are retried, removing conflicts from the batch on each attempt.
        backingStore.transactionSession(holdingIdentity) { session, _ ->
            // We can clear these between retries, data will be retrieved from the backing store
            // anyway.
            transactionDetailsCache.clear()

            resultsToRespondWith.clear()

            // 1. Load our caches with the data persisted in the backing store
            transactionDetailsCache.putAll(
                session.getTransactionDetails(batch.map { it.txId })
            )

            batch.forEach { request ->
                // Already processed -> Return same result as in DB (idempotency) but no need to
                // commit to backing store so is not added to resultsToCommit
                val response = if (transactionDetailsCache[request.txId] != null) {
                    InternalUniquenessCheckResultWithContext(
                        transactionDetailsCache[request.txId]!!.result,
                        isDuplicate = true
                    )
                } else {
                    val timeWindowEvaluationTime = clock.instant()
                    // Time window check
                    if (!isTimeWindowValid(timeWindowEvaluationTime, request.timeWindowLowerBound, request.timeWindowUpperBound)) {
                        InternalUniquenessCheckResultWithContext(
                            UniquenessCheckResultFailureImpl(
                                clock.instant(),
                                UniquenessCheckErrorTimeWindowOutOfBoundsImpl(
                                    timeWindowEvaluationTime,
                                    request.timeWindowLowerBound,
                                    request.timeWindowUpperBound
                                )
                            ),
                            isDuplicate = false
                        )
                    } else {
                        // TODO Return a clearly defined error type
                        // This has not been done yet as it would affect that API.
                        InternalUniquenessCheckResultWithContext(
                            UniquenessCheckResultFailureImpl(
                                clock.instant(),
                                UniquenessCheckErrorUnhandledExceptionImpl(
                                    "UniquenessCheckErrorNotPreviouslyNotarizedException",
                                    "This transaction has not been notarized before"
                                )
                            ),
                            isDuplicate = false
                        )
                    }
                }
                resultsToRespondWith.add(request to response)
            }
        }

        if (log.isDebugEnabled) {
            val numSuccessful = resultsToRespondWith.filter {
                it.second.result is UniquenessCheckResultSuccess }.size

            log.debug { "Finished processing batch for $holdingIdentity. " +
                "$numSuccessful existing notarizations, " +
                "${resultsToRespondWith.size - numSuccessful} non-existing notarizations" }
        }

        return resultsToRespondWith
    }

    private fun handleRejectedRequest(
        request: UniquenessCheckRequestInternal,
        error: UniquenessCheckError
    ): Pair<UniquenessCheckRequestInternal, UniquenessCheckResult> {

        val rejectedResult = UniquenessCheckResultFailureImpl(clock.instant(), error)

        transactionDetailsCache[request.txId] = UniquenessCheckTransactionDetailsInternal(
            request.txId,
            rejectedResult
        )

        return Pair(request, rejectedResult)
    }

    private fun handleSuccessfulRequest(
        request: UniquenessCheckRequestInternal
    ): Pair<UniquenessCheckRequestInternal, UniquenessCheckResult> {

        val txDetails = UniquenessCheckTransactionDetailsInternal(
            request.txId,
            UniquenessCheckResultSuccessImpl(clock.instant())
        )

        transactionDetailsCache[request.txId] = txDetails

        request.inputStates.forEach {
            stateDetailsCache[it] = UniquenessCheckStateDetailsImpl(it, request.txId)
        }

        repeat(request.numOutputStates) {
            val outputStateRef = UniquenessCheckStateRefImpl(request.txId, it)

            stateDetailsCache[outputStateRef] =
                UniquenessCheckStateDetailsImpl(outputStateRef, null)
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
        results: List<Pair<UniquenessCheckRequestInternal, UniquenessCheckResult>>
    ) {
        txOps.commitTransactions(results)

        results.filter { it.second is UniquenessCheckResultSuccess }.forEach { (request, _) ->

            // Insert the output states
            txOps.createUnconsumedStates(
                List(request.numOutputStates) {
                    UniquenessCheckStateRefImpl(request.txId, it)
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
}

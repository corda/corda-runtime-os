package net.corda.uniqueness.checker.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.ExceptionEnvelope
import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.data.uniqueness.UniquenessCheckResponseAvro
import net.corda.data.uniqueness.UniquenessCheckResultMalformedRequestAvro
import net.corda.data.uniqueness.UniquenessCheckResultUnhandledExceptionAvro
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.uniqueness.backingstore.BackingStore
import net.corda.uniqueness.checker.UniquenessChecker
import net.corda.uniqueness.datamodel.common.toAvro
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorInputStateConflictImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorInputStateUnknownImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorReferenceStateConflictImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorReferenceStateUnknownImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorTimeWindowOutOfBoundsImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultFailureImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultSuccessImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckStateDetailsImpl
import net.corda.uniqueness.datamodel.internal.UniquenessCheckTransactionDetailsInternal
import net.corda.uniqueness.datamodel.internal.UniquenessCheckRequestInternal
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.application.uniqueness.model.UniquenessCheckError
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultSuccess
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.uniqueness.data.UniquenessCheckStateDetails
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
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
@Suppress("LongParameterList")
class BatchedUniquenessCheckerImpl(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val configurationReadService: ConfigurationReadService,
    private val subscriptionFactory: SubscriptionFactory,
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    private val clock: Clock,
    private val backingStore: BackingStore
) : UniquenessChecker {

    @Activate
    constructor(
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
        @Reference(service = ConfigurationReadService::class)
        configurationReadService: ConfigurationReadService,
        @Reference(service = SubscriptionFactory::class)
        subscriptionFactory: SubscriptionFactory,
        @Reference(service = ExternalEventResponseFactory::class)
        externalEventResponseFactory: ExternalEventResponseFactory,
        @Reference(service = BackingStore::class)
        backingStore: BackingStore,
    ) : this(
        coordinatorFactory,
        configurationReadService,
        subscriptionFactory,
        externalEventResponseFactory,
        UTCClock(),
        backingStore)

    private companion object {
        const val GROUP_NAME = "uniqueness.checker"

        const val CONFIG_HANDLE = "CONFIG_HANDLE"
        const val SUBSCRIPTION = "SUBSCRIPTION"

        val log: Logger = contextLogger()
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
        HashMap<SecureHash, UniquenessCheckTransactionDetailsInternal>()
    private val stateDetailsCache =
        HashMap<StateRef, UniquenessCheckStateDetails>()

    override fun start() {
        log.info("Uniqueness checker starting")
        lifecycleCoordinator.start()
    }

    override fun stop() {
        log.info("Uniqueness checker stopping")
        lifecycleCoordinator.stop()
    }


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

        // TODO CORE-7250 We have no way to pre-check the number of reference input states in the plugin
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

        // TODO - Re-instate batch processing logic based on number of states if needed - need to
        // establish what batching there is in the message bus layer first
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
                try {
                    processBatch(
                        holdingIdentity.toCorda(),
                        partitionedRequests.map { it.first }
                    ).forEachIndexed { idx, (internalRequest, internalResult) ->
                        results[partitionedRequests[idx].second] = UniquenessCheckResponseAvro(
                            internalRequest.rawTxId, internalResult.toAvro())
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
                    }
                }
            }

        return results
    }

    @Suppress("ComplexMethod", "LongMethod")
    private fun processBatch(
        holdingIdentity: HoldingIdentity,
        batch: List<UniquenessCheckRequestInternal>
    ): List<Pair<UniquenessCheckRequestInternal, UniquenessCheckResult>> {

        val resultsToRespondWith =
            mutableListOf<Pair<UniquenessCheckRequestInternal, UniquenessCheckResult>>()

        log.debug { "Processing batch of ${batch.size} requests for $holdingIdentity" }

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
                                UniquenessCheckErrorInputStateUnknownImpl(unknownInputStates)
                            )
                        // Unknown reference state -> Immediate failure
                        unknownReferenceStates.isNotEmpty() ->
                            handleRejectedRequest(
                                request,
                                UniquenessCheckErrorReferenceStateUnknownImpl(unknownReferenceStates)
                            )
                        // Input state conflict check
                        inputStateConflicts.isNotEmpty() ->
                            handleRejectedRequest(
                                request,
                                UniquenessCheckErrorInputStateConflictImpl(
                                    inputStateConflicts.map { stateDetailsCache[it]!! }
                                )
                            )
                        // Reference state conflict check
                        referenceStateConflicts.isNotEmpty() ->
                            handleRejectedRequest(
                                request,
                                UniquenessCheckErrorReferenceStateConflictImpl(
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
                                UniquenessCheckErrorTimeWindowOutOfBoundsImpl(
                                    timeWindowEvaluationTime,
                                    request.timeWindowLowerBound,
                                    request.timeWindowUpperBound
                                )
                            )
                        // All checks passed
                        else -> handleSuccessfulRequest(request)
                    }

                    resultsToRespondWith.add(result)
                    resultsToCommit.add(result)
                }
            }

            // Now that the processing has finished, we need to commit to the database.
            commitResults(transactionOps, resultsToCommit)
        }

        if (log.isDebugEnabled) {
            val numSuccessful = resultsToRespondWith.filter {
                it.second is UniquenessCheckResultSuccess }.size

            log.debug { "Finished processing batch for $holdingIdentity. " +
                    "$numSuccessful successful, " +
                    "${resultsToRespondWith.size - numSuccessful} rejected" }
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
            val outputStateRef = StateRef(request.txId, it)

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
                    StateRef(request.txId, it)
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
        log.info("Uniqueness checker received event $event")
        when (event) {
            is StartEvent -> {
                configurationReadService.start()
                dependentComponents.registerAndStartAll(coordinator)
            }
            is StopEvent -> {
                dependentComponents.stopAll()
            }
            is RegistrationStatusChangeEvent -> {
                log.info("Uniqueness checker is ${event.status}")

                if (event.status == LifecycleStatus.UP) {
                    coordinator.createManagedResource(CONFIG_HANDLE) {
                        configurationReadService.registerComponentForUpdates(
                            coordinator,
                            setOf(MESSAGING_CONFIG)
                        )
                    }
                } else {
                    coordinator.closeManagedResources(setOf(CONFIG_HANDLE))
                }

                coordinator.updateStatus(event.status)
            }
            is ConfigChangedEvent -> {
                log.info("Received configuration change event, (re)initialising subscription")
                initialiseSubscription(event.config.getConfig(MESSAGING_CONFIG))
            }
            else -> {
                log.warn("Unexpected event ${event}, ignoring")
            }
        }
    }

    private fun initialiseSubscription(config: SmartConfig) {
        lifecycleCoordinator.createManagedResource(SUBSCRIPTION) {
            subscriptionFactory.createDurableSubscription(
                SubscriptionConfig(GROUP_NAME, Schemas.UniquenessChecker.UNIQUENESS_CHECK_TOPIC),
                UniquenessCheckMessageProcessor(
                    this,
                    externalEventResponseFactory
                ),
                config,
                null
            ).also {
                it.start()
            }
        }
    }
}

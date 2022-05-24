package net.corda.uniqueness.checker.impl

import net.corda.data.uniqueness.*
import net.corda.lifecycle.*
import net.corda.uniqueness.checker.UniquenessChecker
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.time.Instant

/**
 * An in-memory implementation of the uniqueness checker component, which does not persist any data,
 * and therefore loses any history of previous requests when a class instance is destroyed.
 *
 * Intended for testing purposes only - DO NOT USE ON A REAL SYSTEM
 */
@Component(service = [UniquenessChecker::class])
class InMemoryUniquenessCheckerImpl(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val clock: Clock
) : UniquenessChecker {

    @Activate
    constructor(
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory
    ) : this(coordinatorFactory, UTCClock())

    companion object {
        private val log: Logger = contextLogger()
    }

    private val responseCache = HashMap<String, UniquenessCheckResponse>()

    // Value of state cache is populated with the consuming tx id when spent, null if unspent
    private val stateCache = HashMap<String, String?>()

    private val lifecycleCoordinator: LifecycleCoordinator = coordinatorFactory
        .createCoordinator<UniquenessChecker>(::eventHandler)

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        log.info("Uniqueness checker starting.")
        lifecycleCoordinator.start()
    }

    override fun stop() {
        log.info("Uniqueness checker stopping.")
        lifecycleCoordinator.stop()
    }

    @Synchronized
    @Suppress("ComplexMethod")
    override fun processRequests(
        requests: List<UniquenessCheckRequest>
    ): List<UniquenessCheckResponse> {
        return requests.map { request ->
            responseCache[request.txId] ?: run {
                val (knownInputStates, unknownInputStates) =
                    request.inputStates.partition { stateCache.containsKey(it) }
                val (knownReferenceStates, unknownReferenceStates) =
                    request.referenceStates.partition { stateCache.containsKey(it) }
                val inputStateConflicts = knownInputStates
                    .filter { stateCache[it] != null && stateCache[it] != request.txId }
                val referenceStateConflicts = knownReferenceStates
                    .filter { stateCache[it] != null && stateCache[it] != request.txId }
                val timeWindowEvaluationTime = clock.instant()

                val response = when {
                    request.numOutputStates < 0 -> {
                        UniquenessCheckResponse(
                            request.txId,
                            UniquenessCheckResultMalformedRequest(
                                "Number of output states cannot be less than 0."
                            )
                        )
                    }

                    unknownInputStates.isNotEmpty() -> {
                        UniquenessCheckResponse(
                            request.txId,
                            UniquenessCheckResultInputStateUnknown(unknownInputStates)
                        )
                    }

                    unknownReferenceStates.isNotEmpty() -> {
                        UniquenessCheckResponse(
                            request.txId,
                            UniquenessCheckResultReferenceStateUnknown(unknownReferenceStates)
                        )
                    }

                    inputStateConflicts.isNotEmpty() -> {
                        UniquenessCheckResponse(
                            request.txId,
                            UniquenessCheckResultInputStateConflict(inputStateConflicts)
                        )
                    }

                    referenceStateConflicts.isNotEmpty() -> {
                        UniquenessCheckResponse(
                            request.txId,
                            UniquenessCheckResultReferenceStateConflict(referenceStateConflicts)
                        )
                    }

                    !isTimeWindowValid(
                        timeWindowEvaluationTime,
                        request.timeWindowLowerBound,
                        request.timeWindowUpperBound) -> {
                        UniquenessCheckResponse(
                            request.txId,
                            UniquenessCheckResultTimeWindowOutOfBounds(
                                timeWindowEvaluationTime,
                                request.timeWindowLowerBound,
                                request.timeWindowUpperBound
                            )
                        )
                    }

                    else -> {
                        // Write unspent states
                        repeat(request.numOutputStates) {
                            stateCache["${request.txId}:${it}"] = null
                        }
                        // Write spent states - overwrites any earlier entries for unspent states
                        stateCache.putAll(request.inputStates.associateWith { request.txId })
                        UniquenessCheckResponse(
                            request.txId,
                            UniquenessCheckResultSuccess(clock.instant())
                        )
                    }
                }

                responseCache[request.txId] = response
                response
            }
        }
    }

    private fun isTimeWindowValid(
        currentTime: Instant,
        timeWindowLowerBound: Instant?,
        timeWindowUpperBound: Instant
    ): Boolean {
        return ((timeWindowLowerBound == null || !timeWindowLowerBound.isAfter(currentTime)) &&
                timeWindowUpperBound.isAfter(currentTime))
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.info("Uniqueness checker received event $event.")
        when (event) {
            is StartEvent -> {
                log.info("Uniqueness checker is UP")
                coordinator.updateStatus(LifecycleStatus.UP)
            }
            is StopEvent -> {
                log.info("Uniqueness checker is DOWN")
                coordinator.updateStatus(LifecycleStatus.DOWN)
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

package net.corda.ledger.utxo.flow.impl.flows.repair

import net.corda.flow.application.services.FlowConfigService
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.MessagingConfig.Subscription.MEDIATOR_PROCESSING_PROCESSOR_TIMEOUT
import net.corda.utilities.seconds
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.annotations.VisibleForTesting
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

class UtxoLoggingLedgerRepairFlow private constructor(
    private val from: Instant,
    private val until: Instant,
    private val duration: Duration,
    private val clock: Clock,
    private val log: Logger
) : SubFlow<Int> {

    private companion object {
        private val STATIC_LOGGER: Logger = LoggerFactory.getLogger(UtxoLoggingLedgerRepairFlow::class.java)
    }

    constructor(from: Instant, until: Instant, duration: Duration) : this(from, until, duration, UTCClock(), STATIC_LOGGER)

    @Suppress("LongParameterList")
    @VisibleForTesting
    constructor(
        from: Instant,
        until: Instant,
        duration: Duration,
        clock: Clock,
        flowConfigService: FlowConfigService,
        flowEngine: FlowEngine,
        log: Logger
    ) : this(from, until, duration, clock, log) {
        this.flowConfigService = flowConfigService
        this.flowEngine = flowEngine
    }

    @CordaInject
    lateinit var flowConfigService: FlowConfigService

    @CordaInject
    private lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(): Int {
        log.info("Starting ledger repair of missing notarized transactions. Repairing transactions that occurred between $from to $until")

        val startTime = System.currentTimeMillis()
        val endTime = Instant.ofEpochMilli(startTime).plus(duration)
        val maxDurationWithoutSuspending = getMaxTimeWithoutSuspending()

        val (
            exceededDuration,
            exceededLastNotarizationTime,
            numberOfNotarizedTransactions,
            numberOfNotNotarizedTransactions,
            numberOfInvalidTransactions,
            numberOfSkippedTransactions
        ) = flowEngine.subFlow(UtxoLedgerRepairFlow(from, until, endTime, maxDurationWithoutSuspending, clock))

        when {
            exceededDuration -> {
                log.info(
                    "Ledger repair result: $numberOfNotarizedTransactions/$numberOfNotNotarizedTransactions/" +
                        "$numberOfInvalidTransactions/$numberOfSkippedTransactions (Notarized/Not-notarized/Invalidated/Skipped). " +
                        "Parameters: $from - $until, $duration. Time taken: " +
                        "${Duration.ofMillis(System.currentTimeMillis() - startTime)}. Exceeded the duration of $duration. " +
                        "There may be more transactions to repair."
                )
            }
            exceededLastNotarizationTime -> {
                log.info(
                    "Ledger repair result: $numberOfNotarizedTransactions/$numberOfNotNotarizedTransactions/" +
                        "$numberOfInvalidTransactions/$numberOfSkippedTransactions (Notarized/Not-notarized/Invalidated/Skipped). " +
                        "Parameters: $from - $until, $duration. Time taken: " +
                        "${Duration.ofMillis(System.currentTimeMillis() - startTime)}. Exceeded the duration of " +
                        "$maxDurationWithoutSuspending between notarizing transactions. There may be more transactions to repair."
                )
            }
            else -> {
                log.info(
                    "Ledger repair result: $numberOfNotarizedTransactions/$numberOfNotNotarizedTransactions/" +
                        "$numberOfInvalidTransactions/$numberOfSkippedTransactions (Notarized/Not-notarized/Invalidated/Skipped). " +
                        "Parameters: $from - $until, $duration. Time taken: " +
                        "${Duration.ofMillis(System.currentTimeMillis() - startTime)}."
                )
            }
        }
        return numberOfNotarizedTransactions
    }

    @VisibleForTesting
    internal fun getMaxTimeWithoutSuspending(): Duration {
        // Shorten the time compared to the configured the mediator's processing timeout, which will normally cause it to timeout. Set the
        // minimum time to 1 second which is the minimum value for the timeout.
        val maxIdleTime = flowConfigService.getConfig(MESSAGING_CONFIG).getLong(MEDIATOR_PROCESSING_PROCESSOR_TIMEOUT)
        return Duration.ofMillis((maxIdleTime - 10.seconds.toMillis()).coerceAtLeast(1.seconds.toMillis()))
    }
}

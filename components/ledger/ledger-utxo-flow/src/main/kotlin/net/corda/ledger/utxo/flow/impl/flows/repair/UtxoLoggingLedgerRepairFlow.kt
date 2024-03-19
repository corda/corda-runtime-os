package net.corda.ledger.utxo.flow.impl.flows.repair

import net.corda.ledger.utxo.flow.impl.notary.PluggableNotaryService
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.VisibilityChecker
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

class UtxoLoggingLedgerRepairFlow(
    private val from: Instant,
    private val until: Instant,
    private val duration: Duration,
    private val clock: Clock = UTCClock()
) : SubFlow<Int> {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(UtxoLoggingLedgerRepairFlow::class.java)
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var pluggableNotaryService: PluggableNotaryService

    @CordaInject
    lateinit var persistenceService: UtxoLedgerPersistenceService

    @CordaInject
    lateinit var visibilityChecker: VisibilityChecker

    @Suppress("NestedBlockDepth")
    @Suspendable
    override fun call(): Int {
        log.info("Starting ledger repair of missing notarized transactions. Repairing transactions that occurred between $from to $until")

        val startTime = System.currentTimeMillis()
        val endTime = Instant.ofEpochMilli(startTime).plus(duration)

        val (
            exceededDuration,
            exceededLastNotarizationTime,
            numberOfNotarizedTransactions,
            numberOfNotNotarizedTransactions,
            numberOfInvalidTransactions,
            numberOfSkippedTransactions
        ) = UtxoLedgerRepairFlow(
            from,
            until,
            endTime,
            clock,
            flowEngine,
            pluggableNotaryService,
            persistenceService,
            visibilityChecker
        ).call()


        if (exceededDuration) {
            log.info(
                "Ledger repair result: $numberOfNotarizedTransactions/$numberOfNotNotarizedTransactions/" +
                    "$numberOfInvalidTransactions/$numberOfSkippedTransactions (Notarized/Not-notarized/Invalidated/Skipped). " +
                    "Parameters: $from - $until, $duration. Time taken: " +
                    "${Duration.ofMillis(System.currentTimeMillis() - startTime)}. Exceeded the duration of $duration. " +
                    "There may be more transactions to repair."
            )
        } else if (exceededLastNotarizationTime) {
            log.info(
                "Ledger repair result: $numberOfNotarizedTransactions/$numberOfNotNotarizedTransactions/" +
                    "$numberOfInvalidTransactions/$numberOfSkippedTransactions (Notarized/Not-notarized/Invalidated/Skipped). " +
                    "Parameters: $from - $until, $duration. Time taken: " +
                    "${Duration.ofMillis(System.currentTimeMillis() - startTime)}. Exceeded the duration of " +
                    "$MAX_DURATION_WITHOUT_SUSPENDING without notarizing a transaction. There may be more transactions to repair."
            )
        } else {
            log.info(
                "Ledger repair result: $numberOfNotarizedTransactions/$numberOfNotNotarizedTransactions/" +
                    "$numberOfInvalidTransactions/$numberOfSkippedTransactions (Notarized/Not-notarized/Invalidated/Skipped). " +
                    "Parameters: $from - $until, $duration. Time taken: " +
                    "${Duration.ofMillis(System.currentTimeMillis() - startTime)}."
            )
        }
        return numberOfNotarizedTransactions
    }
}

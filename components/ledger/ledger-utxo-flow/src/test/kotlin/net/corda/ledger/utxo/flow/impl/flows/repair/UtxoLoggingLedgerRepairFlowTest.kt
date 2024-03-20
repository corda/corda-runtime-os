package net.corda.ledger.utxo.flow.impl.flows.repair

import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.slf4j.Logger
import java.time.Duration
import java.time.Instant

class UtxoLoggingLedgerRepairFlowTest {

    private companion object {
        private val NOW = Instant.now()
    }

    private val flowEngine = mock<FlowEngine>()
    private val log = mock<Logger>()
    private val captor = argumentCaptor<String>()
    private val utxoLoggingLedgerRepairFlow = UtxoLoggingLedgerRepairFlow(
        NOW,
        NOW,
        Duration.ofSeconds(1),
        mock(),
        flowEngine,
        log
    )

    @BeforeEach
    fun beforeEach() {
        doNothing().whenever(log).info(captor.capture())
    }

    @Test
    fun `logs exceeded duration`() {
        whenever(flowEngine.subFlow(any<SubFlow<UtxoLedgerRepairFlow.Result>>()))
            .thenReturn(
                UtxoLedgerRepairFlow.Result(
                    exceededDuration = true,
                    exceededLastNotarizationTime = false,
                    numberOfNotarizedTransactions = 1,
                    numberOfNotNotarizedTransactions = 2,
                    numberOfInvalidTransactions = 3,
                    numberOfSkippedTransactions = 4
                )
            )
        utxoLoggingLedgerRepairFlow.call()
        assertThat(captor.secondValue)
            .contains("1/2/3/4")
            .contains("Exceeded the duration")
            .doesNotContain("between notarizing transactions")
    }

    @Test
    fun `logs exceeded notarization time`() {
        whenever(flowEngine.subFlow(any<SubFlow<UtxoLedgerRepairFlow.Result>>()))
            .thenReturn(
                UtxoLedgerRepairFlow.Result(
                    exceededDuration = false,
                    exceededLastNotarizationTime = true,
                    numberOfNotarizedTransactions = 1,
                    numberOfNotNotarizedTransactions = 2,
                    numberOfInvalidTransactions = 3,
                    numberOfSkippedTransactions = 4
                )
            )
        utxoLoggingLedgerRepairFlow.call()
        assertThat(captor.secondValue)
            .contains("1/2/3/4")
            .contains("Exceeded the duration")
            .contains("between notarizing transactions")
    }

    @Test
    fun `logs completed successfully`() {
        whenever(flowEngine.subFlow(any<SubFlow<UtxoLedgerRepairFlow.Result>>()))
            .thenReturn(
                UtxoLedgerRepairFlow.Result(
                    exceededDuration = false,
                    exceededLastNotarizationTime = false,
                    numberOfNotarizedTransactions = 1,
                    numberOfNotNotarizedTransactions = 2,
                    numberOfInvalidTransactions = 3,
                    numberOfSkippedTransactions = 4
                )
            )
        utxoLoggingLedgerRepairFlow.call()
        assertThat(captor.secondValue)
            .contains("1/2/3/4")
            .doesNotContain("Exceeded the duration")
            .doesNotContain("without notarizing a transaction")
    }
}

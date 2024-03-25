package net.corda.ledger.utxo.flow.impl.flows.repair

import net.corda.flow.application.services.FlowConfigService
import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.MessagingConfig.Subscription.MEDIATOR_PROCESSING_PROCESSOR_TIMEOUT
import net.corda.utilities.minutes
import net.corda.utilities.seconds
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.Logger
import java.time.Duration
import java.time.Instant

class UtxoLoggingLedgerRepairFlowTest {

    private companion object {
        private val NOW = Instant.now()
    }

    private val config = mock<SmartConfig>()
    private val flowConfigService = mock<FlowConfigService>()
    private val flowEngine = mock<FlowEngine>()
    private val log = mock<Logger>()
    private val captor = argumentCaptor<String>()
    private val utxoLoggingLedgerRepairFlow = UtxoLoggingLedgerRepairFlow(
        NOW,
        NOW,
        Duration.ofSeconds(1),
        mock(),
        flowConfigService,
        flowEngine,
        log
    )

    @BeforeEach
    fun beforeEach() {
        doNothing().whenever(log).info(captor.capture())
        doNothing().whenever(log).debug(captor.capture())
        whenever(log.isDebugEnabled).thenReturn(true)
        whenever(flowConfigService.getConfig(MESSAGING_CONFIG)).thenReturn(config)
        whenever(config.getLong(MEDIATOR_PROCESSING_PROCESSOR_TIMEOUT)).thenReturn(2.minutes.toMillis())
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
        assertThat(captor.lastValue)
            .contains("1/2/3/4")
            .contains("Exceeded the duration")
            .doesNotContain("between notarizing transactions")
        verify(log, times(1)).debug(any())
        verify(log, times(1)).info(any())
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
        assertThat(captor.lastValue)
            .contains("1/2/3/4")
            .contains("Exceeded the duration")
            .contains("between notarizing transactions")
        verify(log, times(1)).debug(any())
        verify(log, times(1)).info(any())
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
        assertThat(captor.lastValue)
            .contains("1/2/3/4")
            .doesNotContain("Exceeded the duration")
            .doesNotContain("without notarizing a transaction")
        verify(log, times(1)).debug(any())
        verify(log, times(1)).info(any())
    }

    @Test
    fun `no transactions notarized or invalid logs at debug`() {
        whenever(flowEngine.subFlow(any<SubFlow<UtxoLedgerRepairFlow.Result>>()))
            .thenReturn(
                UtxoLedgerRepairFlow.Result(
                    exceededDuration = false,
                    exceededLastNotarizationTime = false,
                    numberOfNotarizedTransactions = 0,
                    numberOfNotNotarizedTransactions = 2,
                    numberOfInvalidTransactions = 0,
                    numberOfSkippedTransactions = 4
                )
            )
        utxoLoggingLedgerRepairFlow.call()
        assertThat(captor.lastValue)
            .contains("0/2/0/4")
            .doesNotContain("Exceeded the duration")
            .doesNotContain("without notarizing a transaction")
        verify(log, times(2)).debug(any())
    }

    @Test
    fun `gets max time without notarizing from messaging config`() {
        whenever(flowConfigService.getConfig(MESSAGING_CONFIG)).thenReturn(config)
        whenever(config.getLong(MEDIATOR_PROCESSING_PROCESSOR_TIMEOUT)).thenReturn(2.minutes.toMillis())
        val duration = utxoLoggingLedgerRepairFlow.getMaxTimeWithoutSuspending()
        assertThat(duration).isEqualTo(110.seconds)
    }

    @Test
    fun `coerces max time without notarizing to have a minimum value of 1 second`() {
        whenever(flowConfigService.getConfig(MESSAGING_CONFIG)).thenReturn(config)
        whenever(config.getLong(MEDIATOR_PROCESSING_PROCESSOR_TIMEOUT)).thenReturn(1.seconds.toMillis())
        val duration = utxoLoggingLedgerRepairFlow.getMaxTimeWithoutSuspending()
        assertThat(duration).isEqualTo(1.seconds)
    }
}

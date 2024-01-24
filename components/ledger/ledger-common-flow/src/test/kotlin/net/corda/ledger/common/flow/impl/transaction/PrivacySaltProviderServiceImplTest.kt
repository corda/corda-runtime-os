package net.corda.ledger.common.flow.impl.transaction

import net.corda.ledger.common.test.CommonLedgerTest
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever

internal class PrivacySaltProviderServiceImplTest : CommonLedgerTest() {
    private val privacySaltProviderService = PrivacySaltProviderServiceImpl(flowFiberService)

    @BeforeEach
    fun setupPrivacySaltProviderService() {
        val flowId = "fc321a0c-62c6-41a1-85e6-e61870ab93aa"
        val suspendCount = 10
        val ledgerSaltCounter = 1
        val checkpoint = flowFiberService.getExecutingFiber().getExecutionContext().flowCheckpoint

        whenever(checkpoint.flowId).thenReturn(flowId)
        whenever(checkpoint.suspendCount).thenReturn(suspendCount)
        whenever(checkpoint.ledgerSaltCounter).thenReturn(ledgerSaltCounter)
    }

    @Test
    fun `transaction ids are deterministic`() {
        val transaction1 = privacySaltProviderService.generatePrivacySalt()
        val transaction2 = privacySaltProviderService.generatePrivacySalt()
        Assertions.assertThat(transaction1).isEqualTo(transaction2)
    }

    @Test
    fun `different privacy salts are created from different flow ids`() {
        val transaction1 = privacySaltProviderService.generatePrivacySalt()

        val newFlowId = "5e3fb677-6b03-4a0b-8ff4-e2a2465143a7"
        val checkpoint = flowFiberService.getExecutingFiber().getExecutionContext().flowCheckpoint
        whenever(checkpoint.flowId).thenReturn(newFlowId)

        val transaction2 = privacySaltProviderService.generatePrivacySalt()
        Assertions.assertThat(transaction1).isNotEqualTo(transaction2)
    }

    @Test
    fun `different privacy salts are created from different suspendCounts`() {
        val transaction1 = privacySaltProviderService.generatePrivacySalt()

        val newSuspendCount = 50
        val checkpoint = flowFiberService.getExecutingFiber().getExecutionContext().flowCheckpoint
        whenever(checkpoint.suspendCount).thenReturn(newSuspendCount)

        val transaction2 = privacySaltProviderService.generatePrivacySalt()
        Assertions.assertThat(transaction1).isNotEqualTo(transaction2)
    }

    @Test
    fun `different privacy salts are created from different salt counters`() {
        val transaction1 = privacySaltProviderService.generatePrivacySalt()

        val ledgerSaltCounter = 2
        val checkpoint = flowFiberService.getExecutingFiber().getExecutionContext().flowCheckpoint
        whenever(checkpoint.ledgerSaltCounter).thenReturn(ledgerSaltCounter)

        val transaction2 = privacySaltProviderService.generatePrivacySalt()
        Assertions.assertThat(transaction1).isNotEqualTo(transaction2)
    }
}

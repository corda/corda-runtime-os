package net.corda.ledger.common.flow.impl.transaction

import net.corda.ledger.common.test.CommonLedgerTest
import net.corda.ledger.common.testkit.transactionMetadataExample
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever

internal class PrivacySaltProviderServiceImplTest : CommonLedgerTest() {
    private val metadata = transactionMetadataExample()
    private val metadataJson = jsonMarshallingService.format(metadata)
    private val canonicalJson = jsonValidator.canonicalize(metadataJson)
    private val privacySaltProviderService = PrivacySaltProviderServiceImpl(flowFiberService)

    @BeforeEach
    fun setupPrivacySaltProviderService() {
        val flowId = "fc321a0c-62c6-41a1-85e6-e61870ab93aa"
        val suspendCount = 10

        val checkpoint = flowFiberService.getExecutingFiber().getExecutionContext().flowCheckpoint

        whenever(checkpoint.flowId).thenReturn(flowId)
        whenever(checkpoint.suspendCount).thenReturn(suspendCount)
    }

    @Test
    fun `transaction ids are deterministic`() {
        val transaction1 = wireTransactionFactory.create(
            listOf(
                listOf(canonicalJson.toByteArray()),
            ),
            privacySaltProviderService.generatePrivacySalt()
        )

        val transaction2 = wireTransactionFactory.create(
            listOf(
                listOf(canonicalJson.toByteArray()),
            ),
            privacySaltProviderService.generatePrivacySalt()
        )

        Assertions.assertThat(transaction1.id).isEqualTo(transaction2.id)
        Assertions.assertThat(transaction1.privacySalt).isEqualTo(transaction2.privacySalt)
    }

    @Test
    fun `different privacy salts are created from different flow ids`() {
        val transaction1 = wireTransactionFactory.create(
            listOf(
                listOf(canonicalJson.toByteArray()),
            ),
            privacySaltProviderService.generatePrivacySalt()
        )

        val newFlowId = "5e3fb677-6b03-4a0b-8ff4-e2a2465143a7"
        val checkpoint = flowFiberService.getExecutingFiber().getExecutionContext().flowCheckpoint
        whenever(checkpoint.flowId).thenReturn(newFlowId)

        val transaction2 = wireTransactionFactory.create(
            listOf(
                listOf(canonicalJson.toByteArray()),
            ),
            privacySaltProviderService.generatePrivacySalt()
        )

        Assertions.assertThat(transaction1.id).isNotEqualTo(transaction2.id)
        Assertions.assertThat(transaction1.privacySalt).isNotEqualTo(transaction2.privacySalt)
    }

    @Test
    fun `different privacy salts are created from different suspendCounts`() {
        val transaction1 = wireTransactionFactory.create(
            listOf(
                listOf(canonicalJson.toByteArray()),
            ),
            privacySaltProviderService.generatePrivacySalt()
        )

        val newSuspendCount = 50
        val checkpoint = flowFiberService.getExecutingFiber().getExecutionContext().flowCheckpoint
        whenever(checkpoint.suspendCount).thenReturn(newSuspendCount)

        val transaction2 = wireTransactionFactory.create(
            listOf(
                listOf(canonicalJson.toByteArray()),
            ),
            privacySaltProviderService.generatePrivacySalt()
        )

        Assertions.assertThat(transaction1.id).isNotEqualTo(transaction2.id)
        Assertions.assertThat(transaction1.privacySalt).isNotEqualTo(transaction2.privacySalt)
    }
}

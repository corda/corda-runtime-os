package net.corda.ledger.consensual.flow.impl.flows.finality

import net.corda.ledger.consensual.flow.impl.flows.finality.v1.ConsensualFinalityFlowV1
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionInternal
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ConsensualFinalityFlowVersionedFlowFactoryTest {

    private val transaction = mock<ConsensualSignedTransactionInternal>().apply {
        whenever(this.id).thenReturn(mock())
    }
    private val factory = ConsensualFinalityFlowVersionedFlowFactory(transaction)

    @Test
    fun `with platform version 1 creates ConsensualFinalityFlowV1`() {
        assertThat(factory.create(1, emptyList())).isExactlyInstanceOf(ConsensualFinalityFlowV1::class.java)
    }

    @Test
    fun `with platform version greater than 1 creates ConsensualFinalityFlowV1`() {
        assertThat(factory.create(1000, emptyList())).isExactlyInstanceOf(ConsensualFinalityFlowV1::class.java)
    }

    @Test
    fun `with platform version 0 throws exception`() {
        assertThatThrownBy {factory.create(0, emptyList()) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
package net.corda.ledger.consensual.flow.impl.flows.finality

import net.corda.ledger.consensual.flow.impl.flows.finality.v1.ConsensualReceiveFinalityFlowV1
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ConsensualReceiveFinalityFlowVersionedFlowFactoryTest {

    private val factory = ConsensualReceiveFinalityFlowVersionedFlowFactory {}

    @Test
    fun `with platform version 1 creates ConsensualReceiveFinalityFlowV1`() {
        assertThat(factory.create(1, mock())).isExactlyInstanceOf(ConsensualReceiveFinalityFlowV1::class.java)
    }

    @Test
    fun `with platform version greater than 1 creates ConsensualReceiveFinalityFlowV1`() {
        assertThat(factory.create(1000, mock())).isExactlyInstanceOf(ConsensualReceiveFinalityFlowV1::class.java)
    }

    @Test
    fun `with platform version 0 throws exception`() {
        assertThatThrownBy {factory.create(0, mock()) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
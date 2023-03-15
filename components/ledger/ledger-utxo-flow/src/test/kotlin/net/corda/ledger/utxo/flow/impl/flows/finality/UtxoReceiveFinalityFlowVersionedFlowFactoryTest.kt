package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.utxo.flow.impl.flows.finality.v1.UtxoReceiveFinalityFlowV1
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class UtxoReceiveFinalityFlowVersionedFlowFactoryTest {

    private val factory = UtxoReceiveFinalityFlowVersionedFlowFactory {}

    @Test
    fun `with platform version 1 creates UtxoReceiveFinalityFlowV1`() {
        assertThat(factory.create(1, mock())).isExactlyInstanceOf(UtxoReceiveFinalityFlowV1::class.java)
    }

    @Test
    fun `with platform version greater than 1 creates UtxoReceiveFinalityFlowV1`() {
        assertThat(factory.create(1000, mock())).isExactlyInstanceOf(UtxoReceiveFinalityFlowV1::class.java)
    }

    @Test
    fun `with platform version 0 throws exception`() {
        assertThatThrownBy {factory.create(0, mock()) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
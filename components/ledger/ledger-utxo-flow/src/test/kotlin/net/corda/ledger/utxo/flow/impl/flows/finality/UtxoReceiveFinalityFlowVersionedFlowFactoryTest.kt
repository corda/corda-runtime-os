package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.utxo.flow.impl.flows.finality.v1.UtxoReceiveFinalityFlowV1
import net.corda.libs.platform.PlatformVersion
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.test.assertSame

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
    fun `with last potential 5_0 platform version creates UtxoReceiveFinalityFlowV1`() {
        val flow = factory.create(PlatformVersion.CORDA_5_0.value, mock())
        assertThat(flow).isExactlyInstanceOf(UtxoReceiveFinalityFlowV1::class.java)
        assertSame(UtxoFinalityVersion.V1, (flow as UtxoReceiveFinalityFlowV1).version)
    }

    @Test
    fun `with first 5_1 platform version creates UtxoReceiveFinalityFlowV1`() {
        val flow = factory.create(PlatformVersion.CORDA_5_1.value, mock())
        assertThat(flow).isExactlyInstanceOf(UtxoReceiveFinalityFlowV1::class.java)
        assertSame(UtxoFinalityVersion.V2, (flow as UtxoReceiveFinalityFlowV1).version)
    }

    @Test
    fun `with platform version 0 throws exception`() {
        assertThatThrownBy {factory.create(0, mock()) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
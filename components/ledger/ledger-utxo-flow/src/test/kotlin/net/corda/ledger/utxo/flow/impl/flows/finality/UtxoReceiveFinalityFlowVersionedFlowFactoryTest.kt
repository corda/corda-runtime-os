package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.utxo.flow.impl.flows.finality.v1.UtxoReceiveFinalityFlowV1
import net.corda.libs.platform.PlatformVersion
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class UtxoReceiveFinalityFlowVersionedFlowFactoryTest {

    private val factory = UtxoReceiveFinalityFlowVersionedFlowFactory {}

    @Test
    fun `with platform version 1 throws a CordaRuntimeException`() {
        assertThatThrownBy { factory.create(1, mock()) }.isInstanceOf(CordaRuntimeException::class.java)
    }

    @Test
    fun `with last potential 5_0 platform version throws a CordaRuntimeException`() {
        assertThatThrownBy { factory.create(PlatformVersion.CORDA_5_1.value - 1, mock()) }.isInstanceOf(
            CordaRuntimeException::class.java
        )
    }

    @Test
    fun `with first 5_1 platform version creates UtxoReceiveFinalityFlowV1`() {
        val flow = factory.create(PlatformVersion.CORDA_5_1.value, mock())
        assertThat(flow).isExactlyInstanceOf(UtxoReceiveFinalityFlowV1::class.java)
    }

    @Test
    fun `with platform version 0 throws exception`() {
        assertThatThrownBy { factory.create(0, mock()) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}

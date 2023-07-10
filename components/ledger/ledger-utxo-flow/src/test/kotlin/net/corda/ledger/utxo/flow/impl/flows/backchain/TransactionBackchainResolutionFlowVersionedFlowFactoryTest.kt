package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.ledger.utxo.flow.impl.flows.backchain.base.TransactionBackchainResolutionFlowBase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.test.assertSame

class TransactionBackchainResolutionFlowVersionedFlowFactoryTest {

    private val factory = TransactionBackchainResolutionFlowVersionedFlowFactory(mock())

    @Test
    fun `with platform version 1 creates TransactionBackchainResolutionFlowV1`() {
        val flow = factory.create(1, mock())
        assertThat(flow).isExactlyInstanceOf(TransactionBackchainResolutionFlowBase::class.java)
        assertSame(
            TransactionBackChainResolutionVersion.V1,
            (flow as TransactionBackchainResolutionFlowBase).version
        )
    }

    @Test
    fun `with platform version 50099 creates TransactionBackchainResolutionFlowV1`() {
        val flow = factory.create(50099, mock())
        assertThat(flow).isExactlyInstanceOf(TransactionBackchainResolutionFlowBase::class.java)
        assertSame(
            TransactionBackChainResolutionVersion.V1,
            (flow as TransactionBackchainResolutionFlowBase).version
        )
    }

    @Test
    fun `with platform version 50100 creates TransactionBackchainResolutionFlowV2`() {
        val flow = factory.create(50100, mock())
        assertThat(flow).isExactlyInstanceOf(TransactionBackchainResolutionFlowBase::class.java)
        assertSame(
            TransactionBackChainResolutionVersion.V2,
            (flow as TransactionBackchainResolutionFlowBase).version
        )
    }

    @Test
    fun `with platform version 50199 creates TransactionBackchainResolutionFlowV2`() {
        val flow = factory.create(50199, mock())
        assertThat(flow).isExactlyInstanceOf(TransactionBackchainResolutionFlowBase::class.java)
        assertSame(
            TransactionBackChainResolutionVersion.V2,
            (flow as TransactionBackchainResolutionFlowBase).version
        )
    }

    @Test
    fun `with platform version 0 throws exception`() {
        assertThatThrownBy { factory.create(0, mock()) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
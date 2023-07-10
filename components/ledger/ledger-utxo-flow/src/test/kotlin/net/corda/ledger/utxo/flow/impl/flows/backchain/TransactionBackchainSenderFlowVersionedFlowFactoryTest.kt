package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.ledger.utxo.flow.impl.flows.backchain.base.TransactionBackchainSenderFlowBase
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.test.assertSame

class TransactionBackchainSenderFlowVersionedFlowFactoryTest {

    private val factory = TransactionBackchainSenderFlowVersionedFlowFactory(mock())

    @Test
    fun `with platform version 1 creates TransactionBackchainSenderFlowV1`() {
        val flow = factory.create(1, listOf(mock()))
        Assertions.assertThat(flow).isExactlyInstanceOf(TransactionBackchainSenderFlowBase::class.java)
        assertSame(
            TransactionBackChainResolutionVersion.V1,
            (flow as TransactionBackchainSenderFlowBase).version
        )
    }

    @Test
    fun `with platform version 50099 creates TransactionBackchainSenderFlowV1`() {
        val flow = factory.create(50099, listOf(mock()))
        Assertions.assertThat(flow).isExactlyInstanceOf(TransactionBackchainSenderFlowBase::class.java)
        assertSame(
            TransactionBackChainResolutionVersion.V1,
            (flow as TransactionBackchainSenderFlowBase).version
        )
    }

    @Test
    fun `with platform version 50100 creates TransactionBackchainSenderFlowV2`() {
        val flow = factory.create(50100, listOf(mock()))
        Assertions.assertThat(flow).isExactlyInstanceOf(TransactionBackchainSenderFlowBase::class.java)
        assertSame(
            TransactionBackChainResolutionVersion.V2,
            (flow as TransactionBackchainSenderFlowBase).version
        )
    }

    @Test
    fun `with platform version 50199 creates TransactionBackchainSenderFlowV2`() {
        val flow = factory.create(50199, listOf(mock()))
        Assertions.assertThat(flow).isExactlyInstanceOf(TransactionBackchainSenderFlowBase::class.java)
        assertSame(
            TransactionBackChainResolutionVersion.V2,
            (flow as TransactionBackchainSenderFlowBase).version
        )
    }

    @Test
    fun `with platform version 0 throws exception`() {
        assertThatThrownBy {factory.create(0, listOf(mock())) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
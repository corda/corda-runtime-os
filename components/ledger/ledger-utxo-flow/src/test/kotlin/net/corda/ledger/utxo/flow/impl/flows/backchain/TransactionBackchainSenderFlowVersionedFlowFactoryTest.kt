package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.ledger.utxo.flow.impl.flows.backchain.v1.TransactionBackchainSenderFlowV1
import net.corda.ledger.utxo.flow.impl.flows.backchain.v2.TransactionBackchainSenderFlowV2
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class TransactionBackchainSenderFlowVersionedFlowFactoryTest {

    private val factory = TransactionBackchainSenderFlowVersionedFlowFactory(mock())

    @Test
    fun `with platform version 1 creates TransactionBackchainSenderFlowV1`() {
        assertThat(factory.create(1, listOf(mock()))).isExactlyInstanceOf(TransactionBackchainSenderFlowV1::class.java)
    }

    @Test
    fun `with platform version 50099 creates TransactionBackchainSenderFlowV1`() {
        assertThat(factory.create(50099, listOf(mock()))).isExactlyInstanceOf(TransactionBackchainSenderFlowV1::class.java)
    }

    @Test
    fun `with platform version 50100 creates TransactionBackchainSenderFlowV2`() {
        assertThat(factory.create(50100, listOf(mock()))).isExactlyInstanceOf(TransactionBackchainSenderFlowV2::class.java)
    }

    @Test
    fun `with platform version 50199 creates TransactionBackchainSenderFlowV2`() {
        assertThat(factory.create(50199, listOf(mock()))).isExactlyInstanceOf(TransactionBackchainSenderFlowV2::class.java)
    }

    @Test
    fun `with platform version 0 throws exception`() {
        assertThatThrownBy {factory.create(0, listOf(mock())) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.ledger.utxo.flow.impl.flows.backchain.v1.TransactionBackchainSenderFlowV1
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
    fun `with platform version greater than 1 creates TransactionBackchainSenderFlowV1`() {
        assertThat(factory.create(1000, listOf(mock()))).isExactlyInstanceOf(TransactionBackchainSenderFlowV1::class.java)
    }

    @Test
    fun `with platform version 0 throws exception`() {
        assertThatThrownBy {factory.create(0, listOf(mock())) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
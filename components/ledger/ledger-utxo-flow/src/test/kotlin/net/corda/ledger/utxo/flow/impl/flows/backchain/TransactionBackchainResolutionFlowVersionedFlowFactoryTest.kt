package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.ledger.utxo.flow.impl.flows.backchain.v1.TransactionBackchainResolutionFlowV1
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class TransactionBackchainResolutionFlowVersionedFlowFactoryTest {
    
    private val factory = TransactionBackchainResolutionFlowVersionedFlowFactory(mock())

    @Test
    fun `with platform version 1 creates TransactionBackchainResolutionFlowV1`() {
        assertThat(factory.create(1, mock())).isExactlyInstanceOf(TransactionBackchainResolutionFlowV1::class.java)
    }

    @Test
    fun `with platform version greater than 1 creates TransactionBackchainResolutionFlowV1`() {
        assertThat(factory.create(1000, mock())).isExactlyInstanceOf(TransactionBackchainResolutionFlowV1::class.java)
    }

    @Test
    fun `with platform version 0 throws exception`() {
        assertThatThrownBy {factory.create(0, mock()) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
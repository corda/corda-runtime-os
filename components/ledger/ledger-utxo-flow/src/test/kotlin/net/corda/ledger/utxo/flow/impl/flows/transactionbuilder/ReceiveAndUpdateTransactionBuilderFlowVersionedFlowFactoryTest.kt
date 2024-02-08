package net.corda.ledger.utxo.flow.impl.flows.transactionbuilder

import net.corda.ledger.utxo.flow.impl.flows.transactionbuilder.v1.ReceiveAndUpdateTransactionBuilderFlowV1
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ReceiveAndUpdateTransactionBuilderFlowVersionedFlowFactoryTest {

    private val factory = ReceiveAndUpdateTransactionBuilderFlowVersionedFlowFactory(mock())

    @Test
    fun `with platform version 1 creates ReceiveAndUpdateTransactionBuilderFlowV1`() {
        assertThat(factory.create(1, mock())).isExactlyInstanceOf(ReceiveAndUpdateTransactionBuilderFlowV1::class.java)
    }

    @Test
    fun `with platform version greater than 1 creates ReceiveAndUpdateTransactionBuilderFlowV1`() {
        assertThat(factory.create(1000, mock())).isExactlyInstanceOf(ReceiveAndUpdateTransactionBuilderFlowV1::class.java)
    }

    @Test
    fun `with platform version 0 throws exception`() {
        assertThatThrownBy { factory.create(0, mock()) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}

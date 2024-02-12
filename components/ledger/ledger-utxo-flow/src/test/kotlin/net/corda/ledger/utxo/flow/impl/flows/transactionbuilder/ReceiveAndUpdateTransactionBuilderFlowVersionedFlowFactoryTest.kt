package net.corda.ledger.utxo.flow.impl.flows.transactionbuilder

import net.corda.ledger.utxo.flow.impl.flows.transactionbuilder.v1.ReceiveAndUpdateTransactionBuilderFlowV1
import net.corda.ledger.utxo.flow.impl.flows.transactionbuilder.v2.ReceiveAndUpdateTransactionBuilderFlowV2
import net.corda.libs.platform.PlatformVersion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ReceiveAndUpdateTransactionBuilderFlowVersionedFlowFactoryTest {

    private val factory = ReceiveAndUpdateTransactionBuilderFlowVersionedFlowFactory(mock())

    @Test
    fun `with platform version CORDA_5_2 creates ReceiveAndUpdateTransactionBuilderFlowV2`() {
        assertThat(factory.create(PlatformVersion.CORDA_5_2.value, mock())).isExactlyInstanceOf(
            ReceiveAndUpdateTransactionBuilderFlowV2::class.java)
    }

    @Test
    fun `with platform version 99999 creates ReceiveAndUpdateTransactionBuilderFlowV2`() {
        assertThat(factory.create(99999, mock())).isExactlyInstanceOf(
            ReceiveAndUpdateTransactionBuilderFlowV2::class.java)
    }

    @Test
    fun `with platform version CORDA_5_1 creates ReceiveAndUpdateTransactionBuilderFlowV1`() {
        assertThat(factory.create(PlatformVersion.CORDA_5_1.value, mock())).isExactlyInstanceOf(
            ReceiveAndUpdateTransactionBuilderFlowV1::class.java)
    }

    @Test
    fun `with platform version CORDA_5_0 creates ReceiveAndUpdateTransactionBuilderFlowV1`() {
        assertThat(factory.create(PlatformVersion.CORDA_5_0.value, mock())).isExactlyInstanceOf(
            ReceiveAndUpdateTransactionBuilderFlowV1::class.java)
    }
}

package net.corda.ledger.utxo.flow.impl.flows.transactionbuilder

import net.corda.ledger.utxo.flow.impl.flows.transactionbuilder.v1.SendTransactionBuilderDiffFlowV1
import net.corda.ledger.utxo.flow.impl.flows.transactionbuilder.v2.SendTransactionBuilderDiffFlowV2
import net.corda.libs.platform.PlatformVersion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class SendTransactionBuilderDiffFlowVersionedFlowFactoryTest {

    private val factory = SendTransactionBuilderDiffFlowVersionedFlowFactory(mock())

    @Test
    fun `with platform version CORDA_5_2 creates ReceiveAndUpdateTransactionBuilderFlowV2`() {
        assertThat(factory.create(PlatformVersion.CORDA_5_2.value, listOf(mock()))).isExactlyInstanceOf(
            SendTransactionBuilderDiffFlowV2::class.java)
    }

    @Test
    fun `with platform version 99999 creates ReceiveAndUpdateTransactionBuilderFlowV2`() {
        assertThat(factory.create(99999, listOf(mock()))).isExactlyInstanceOf(
            SendTransactionBuilderDiffFlowV2::class.java)
    }

    @Test
    fun `with platform version CORDA_5_1 creates ReceiveAndUpdateTransactionBuilderFlowV1`() {
        assertThat(factory.create(PlatformVersion.CORDA_5_1.value, listOf(mock()))).isExactlyInstanceOf(
            SendTransactionBuilderDiffFlowV1::class.java)
    }

    @Test
    fun `with platform version CORDA_5_0 creates ReceiveAndUpdateTransactionBuilderFlowV1`() {
        assertThat(factory.create(PlatformVersion.CORDA_5_0.value, listOf(mock()))).isExactlyInstanceOf(
            SendTransactionBuilderDiffFlowV1::class.java)
    }
}

package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.ledger.utxo.flow.impl.flows.backchain.v1.TransactionBackchainSenderFlowV1
import net.corda.libs.platform.PlatformVersion.CORDA_5_1
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class TransactionBackchainSenderFlowVersionedFlowFactoryTest {

    private val factory = TransactionBackchainSenderFlowVersionedFlowFactory(mock())

    @Test
    fun `with platform version 1 throws a CordaRuntimeException`() {
        assertThatThrownBy { factory.create(1, listOf(mock())) }.isInstanceOf(CordaRuntimeException::class.java)
    }

    @Test
    fun `with last potential 5_0 platform version throws a CordaRuntimeException`() {
        assertThatThrownBy {
            factory.create(
                CORDA_5_1.value - 1,
                listOf(mock())
            )
        }.isInstanceOf(CordaRuntimeException::class.java)
    }

    @Test
    fun `with first 5_1 platform version creates TransactionBackchainSenderFlowV1`() {
        val flow = factory.create(CORDA_5_1.value, listOf(mock()))
        assertThat(flow).isExactlyInstanceOf(TransactionBackchainSenderFlowV1::class.java)
    }

    @Test
    fun `with platform version 50199 creates TransactionBackchainSenderFlowV1`() {
        val flow = factory.create(50199, listOf(mock()))
        assertThat(flow).isExactlyInstanceOf(TransactionBackchainSenderFlowV1::class.java)
    }

    @Test
    fun `with platform version 0 throws exception`() {
        assertThatThrownBy { factory.create(0, listOf(mock())) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}

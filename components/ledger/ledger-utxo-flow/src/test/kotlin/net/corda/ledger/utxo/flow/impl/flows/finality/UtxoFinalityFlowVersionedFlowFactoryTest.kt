package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.utxo.flow.impl.flows.finality.v1.UtxoFinalityFlowV1
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.libs.platform.PlatformVersion.CORDA_5_1
import net.corda.libs.platform.PlatformVersion.CORDA_5_0
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlow
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertSame

class UtxoFinalityFlowVersionedFlowFactoryTest {

    private val transaction = mock<UtxoSignedTransactionInternal>().apply {
        whenever(this.id).thenReturn(mock())
    }
    private val factory = UtxoFinalityFlowVersionedFlowFactory(transaction, PluggableNotaryClientFlow::class.java)

    @Test
    fun `with platform version 1 creates UtxoFinalityFlowV1`() {
        assertThat(factory.create(1, emptyList())).isExactlyInstanceOf(UtxoFinalityFlowV1::class.java)
    }

    @Test
    fun `with platform version greater than 1 creates UtxoFinalityFlowV1`() {
        assertThat(factory.create(1000, emptyList())).isExactlyInstanceOf(UtxoFinalityFlowV1::class.java)
    }

    @Test
    fun `with last potential 5_0 platform version creates UtxoFinalityFlowV1`() {
        val flow = factory.create(CORDA_5_0.value, emptyList())
        assertThat(flow).isExactlyInstanceOf(UtxoFinalityFlowV1::class.java)
        assertSame(UtxoFinalityVersion.V1, (flow as UtxoFinalityFlowV1).version)
    }

    @Test
    fun `with first 5_1 platform version creates UtxoFinalityFlowV1`() {
        val flow = factory.create(CORDA_5_1.value, emptyList())
        assertThat(flow).isExactlyInstanceOf(UtxoFinalityFlowV1::class.java)
        assertSame(UtxoFinalityVersion.V2, (flow as UtxoFinalityFlowV1).version)
    }

    @Test
    fun `with platform version 0 throws exception`() {
        assertThatThrownBy {factory.create(0, emptyList()) }.isInstanceOf(IllegalArgumentException::class.java)
    }


}
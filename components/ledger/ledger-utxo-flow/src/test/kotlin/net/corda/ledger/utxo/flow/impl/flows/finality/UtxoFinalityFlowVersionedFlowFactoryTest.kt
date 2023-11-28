package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.utxo.flow.impl.PluggableNotaryDetails
import net.corda.ledger.utxo.flow.impl.flows.finality.v1.UtxoFinalityFlowV1
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.libs.platform.PlatformVersion.CORDA_5_1
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlow
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UtxoFinalityFlowVersionedFlowFactoryTest {

    private val transaction = mock<UtxoSignedTransactionInternal>().apply {
        whenever(this.id).thenReturn(mock())
    }
    private val pluggableNotaryDetails = mock<PluggableNotaryDetails>().apply {
        whenever(this.flowClass).thenReturn(PluggableNotaryClientFlow::class.java)
    }
    private val factory = UtxoFinalityFlowVersionedFlowFactory(transaction, pluggableNotaryDetails)

    @Test
    fun `with platform version 1 throws a CordaRuntimeException`() {
        assertThatThrownBy { factory.create(1, mock()) }.isInstanceOf(CordaRuntimeException::class.java)
    }

    @Test
    fun `with last potential 5_0 platform version throws a CordaRuntimeException`() {
        assertThatThrownBy {
            factory.create(
                CORDA_5_1.value - 1,
                mock()
            )
        }.isInstanceOf(CordaRuntimeException::class.java)
    }

    @Test
    fun `with first 5_1 platform version creates UtxoFinalityFlowV1`() {
        val flow = factory.create(CORDA_5_1.value, emptyList())
        assertThat(flow).isExactlyInstanceOf(UtxoFinalityFlowV1::class.java)
    }

    @Test
    fun `with platform version 0 throws exception`() {
        assertThatThrownBy {factory.create(0, emptyList()) }.isInstanceOf(IllegalArgumentException::class.java)
    }


}
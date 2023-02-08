package net.corda.ledger.utxo.flow.impl.transaction.serializer.amqp

import net.corda.ledger.utxo.flow.impl.transaction.filtered.UtxoFilteredTransactionBuilderImpl
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class UtxoFilteredTransactionBuilderSerializerTest {

    @Test
    fun `cannot serialize utxo filtered transaction builder`() {
        val builder = UtxoFilteredTransactionBuilderImpl(mock(), mock())
        assertThatThrownBy { UtxoFilteredTransactionBuilderSerializer().toProxy(builder, mock()) }
            .isInstanceOf(CordaRuntimeException::class.java)
    }

    @Test
    fun `cannot deserialize utxo filtered transaction builder proxy`() {
        assertThatThrownBy { UtxoFilteredTransactionBuilderSerializer().fromProxy(UtxoFilteredTransactionBuilderProxy(), mock()) }
            .isInstanceOf(CordaRuntimeException::class.java)
    }

}
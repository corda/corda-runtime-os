package net.corda.ledger.utxo.flow.impl.transaction.serializer.amqp

import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderImpl
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class UtxoTransactionBuilderSerializerTest {

    @Test
    fun `cannot serialize utxo transaction builder`() {
        val builder = UtxoTransactionBuilderImpl(mock())
        assertThatThrownBy { UtxoTransactionBuilderSerializer().toProxy(builder, mock()) }
            .isInstanceOf(CordaRuntimeException::class.java)
    }

    @Test
    fun `cannot deserialize utxo transaction builder proxy`() {
        assertThatThrownBy { UtxoTransactionBuilderSerializer().fromProxy(UtxoTransactionBuilderProxy(), mock()) }
            .isInstanceOf(CordaRuntimeException::class.java)
    }

}
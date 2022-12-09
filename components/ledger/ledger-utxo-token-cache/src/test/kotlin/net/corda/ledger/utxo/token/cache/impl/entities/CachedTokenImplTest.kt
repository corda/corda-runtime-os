package net.corda.ledger.utxo.token.cache.impl.entities

import net.corda.data.ledger.utxo.token.selection.data.Token
import net.corda.data.ledger.utxo.token.selection.data.TokenAmount
import net.corda.ledger.utxo.token.cache.converters.EntityConverter
import net.corda.ledger.utxo.token.cache.entities.CachedTokenImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal

class CachedTokenImplTest {

    private val entityConverter = mock<EntityConverter>()
    private val token = Token().apply {
        this.stateRef = "s1"
        this.amount = TokenAmount()
    }

    @Test
    fun `amount returns amount from underlying object`() {
        val tokenAmount = BigDecimal(1)
        whenever(entityConverter.amountToBigDecimal(token.amount)).thenReturn(tokenAmount)

        assertThat(CachedTokenImpl(token, entityConverter).amount).isEqualTo(tokenAmount)
    }

    @Test
    fun `state ref returns state ref from underlying object`() {
        assertThat(CachedTokenImpl(token, entityConverter).stateRef).isEqualTo("s1")
    }

    @Test
    fun `to avro returns underlying object`() {
        assertThat(CachedTokenImpl(token, entityConverter).toAvro()).isSameAs(token)
    }
}

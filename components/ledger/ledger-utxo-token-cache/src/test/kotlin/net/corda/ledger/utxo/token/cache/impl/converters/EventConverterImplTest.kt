package net.corda.ledger.utxo.token.cache.impl.converters

import net.corda.data.ledger.utxo.token.selection.data.TokenClaimQuery
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimRelease
import net.corda.data.ledger.utxo.token.selection.data.TokenLedgerChange
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.ledger.utxo.token.cache.converters.EntityConverter
import net.corda.ledger.utxo.token.cache.converters.EventConverterImpl
import net.corda.ledger.utxo.token.cache.entities.ClaimQuery
import net.corda.ledger.utxo.token.cache.entities.ClaimRelease
import net.corda.ledger.utxo.token.cache.entities.LedgerChange
import net.corda.ledger.utxo.token.cache.impl.POOL_CACHE_KEY
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal

class EventConverterImplTest {

    private val entityConverter = mock<EntityConverter>()
    private val claimQuery = ClaimQuery("","", BigDecimal(0), "", "", POOL_CACHE_KEY)
    private val claimRelease = ClaimRelease("","", setOf(), POOL_CACHE_KEY)
    private val ledgerChange = LedgerChange(POOL_CACHE_KEY,"","", listOf(), listOf())

    @BeforeEach
    fun setup() {
        whenever(entityConverter.toClaimQuery(any(), any())).thenReturn(claimQuery)
        whenever(entityConverter.toClaimRelease(any(), any())).thenReturn(claimRelease)
        whenever(entityConverter.toLedgerChange(any(), any())).thenReturn(ledgerChange)
    }

    @Test
    fun `converts a TokenClaimQuery payload to a ClaimQuery event`() {
        val inputEvent = TokenPoolCacheEvent().apply {
            poolKey = POOL_CACHE_KEY
            payload = TokenClaimQuery()
        }

        val outputEvent = EventConverterImpl(entityConverter).convert(inputEvent)

        assertThat(outputEvent).isSameAs(claimQuery)
    }

    @Test
    fun `converts a TokenClaimRelease payload to a ClaimRelease event`() {
        val inputEvent = TokenPoolCacheEvent().apply {
            poolKey = POOL_CACHE_KEY
            payload = TokenClaimRelease()
        }

        val outputEvent = EventConverterImpl(entityConverter).convert(inputEvent)

        assertThat(outputEvent).isSameAs(claimRelease)
    }

    @Test
    fun `converts a TokenLedgerChange payload to a LedgerChange event`() {
        val inputEvent = TokenPoolCacheEvent().apply {
            poolKey = POOL_CACHE_KEY
            payload = TokenLedgerChange()
        }

        val outputEvent = EventConverterImpl(entityConverter).convert(inputEvent)

        assertThat(outputEvent).isSameAs(ledgerChange)
    }

    @Test
    fun `convert throws if the input event is null`() {
        assertThatIllegalStateException().isThrownBy {
            EventConverterImpl(entityConverter).convert(null)
        }
    }

    @Test
    fun `convert throws if the payload is null`() {
        val inputEvent = TokenPoolCacheEvent().apply {
            poolKey = POOL_CACHE_KEY
            payload = null
        }

        assertThatIllegalStateException().isThrownBy {
            EventConverterImpl(entityConverter).convert(inputEvent)
        }
    }

    @Test
    fun `convert throws if event type not recognised`() {
        val inputEvent = TokenPoolCacheEvent().apply {
            poolKey = POOL_CACHE_KEY
            payload = "not a valid payload"
        }

        assertThatIllegalStateException().isThrownBy {
            EventConverterImpl(entityConverter).convert(inputEvent)
        }
    }
}

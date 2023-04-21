package net.corda.ledger.utxo.token.cache.impl.services

import net.corda.data.ledger.utxo.token.selection.data.Token
import net.corda.data.ledger.utxo.token.selection.data.TokenAmount
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.ledger.utxo.token.cache.converters.EntityConverterImpl
import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.ClaimQuery
import net.corda.ledger.utxo.token.cache.entities.TokenCacheImpl
import net.corda.ledger.utxo.token.cache.impl.POOL_CACHE_KEY
import net.corda.ledger.utxo.token.cache.services.SimpleTokenFilterStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.nio.ByteBuffer

class SimpleTokenFilterStrategyTest {

    private val token1 = createToken("s1", "t1", "h1")
    private val token2 = createToken("s2", "t2 t1 t3", "h1")
    private val token3 = createToken("s3", "t4", null)
    private val inputTokens = listOf(token1, token2, token3)
    private val target = SimpleTokenFilterStrategy()

    /**
     * Matching rule:
     * null value in the query criteria matches anything
     */
    @Test
    fun `null tag and owner criteria should match all`() {
        val bigDecimal = BigDecimal(123.456)
        val targetAmount = TokenAmount().apply {
            unscaledValue = ByteBuffer.wrap(bigDecimal.unscaledValue().toByteArray())
            scale = bigDecimal.scale()
        }

        val t1 = Token().apply {
            amount = targetAmount
        }
        val t2 = Token().apply {
            amount = targetAmount
        }
        val t3 = Token().apply {
            amount = targetAmount
        }
        val pool = TokenPoolCacheState().apply {
            poolKey = POOL_CACHE_KEY
            availableTokens = listOf(t1, t2, t3)
        }
        val tc = TokenCacheImpl(pool, EntityConverterImpl())
        val query = ClaimQuery("r1", "f1", BigDecimal(1), null, null, POOL_CACHE_KEY)

        val result = target.filterTokens(tc, query).toList()
        println(result)
    }

    @Test
    fun `tag regex should match token tag null owner matches anything`() {
        val query = ClaimQuery("r1", "f1", BigDecimal(1), "(t1)", null, POOL_CACHE_KEY)

        assertThat(target.filterTokens(inputTokens, query)).containsOnly(token1, token2)
    }

    @Test
    fun `owner hash should match token owner hash null tag regex matches anything`() {
        val query = ClaimQuery("r1", "f1", BigDecimal(1), null, "h1", POOL_CACHE_KEY)

        assertThat(target.filterTokens(inputTokens, query)).containsOnly(token1, token2)
    }

    @Test
    fun `owner hash and tag should match token owner hash and tag`() {
        val query = ClaimQuery("r1", "f1", BigDecimal(1), "t2", "h1", POOL_CACHE_KEY)

        assertThat(target.filterTokens(inputTokens, query)).containsOnly(token2)
    }

    private fun createToken(stateRef: String, tag: String?, ownerHash: String?): CachedToken {
        return mock<CachedToken>().apply {
            whenever(this.stateRef).thenReturn(stateRef)
            whenever(this.tag).thenReturn(tag)
            whenever(this.ownerHash).thenReturn(ownerHash)
        }
    }
}

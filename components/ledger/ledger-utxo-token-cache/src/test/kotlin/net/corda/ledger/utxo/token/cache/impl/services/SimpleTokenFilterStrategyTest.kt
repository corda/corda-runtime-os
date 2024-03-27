package net.corda.ledger.utxo.token.cache.impl.services

import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.ClaimQuery
import net.corda.ledger.utxo.token.cache.entities.internal.TokenCacheImpl
import net.corda.ledger.utxo.token.cache.impl.POOL_KEY
import net.corda.ledger.utxo.token.cache.services.SimpleTokenFilterStrategy
import net.corda.utilities.time.UTCClock
import net.corda.v5.ledger.utxo.token.selection.Strategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration

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
        val tc = TokenCacheImpl(Duration.ZERO, UTCClock()).get(Strategy.RANDOM)
        val query = ClaimQuery("r1", "f1", BigDecimal(1), null, null, POOL_KEY, Strategy.RANDOM)

        val result = target.filterTokens(tc, query).toList()
        println(result)
    }

    @Test
    fun `tag regex should match token tag null owner matches anything`() {
        val query = ClaimQuery("r1", "f1", BigDecimal(1), "(t1)", null, POOL_KEY, Strategy.RANDOM)

        assertThat(target.filterTokens(inputTokens, query)).containsOnly(token1, token2)
    }

    @Test
    fun `owner hash should match token owner hash null tag regex matches anything`() {
        val query = ClaimQuery("r1", "f1", BigDecimal(1), null, "h1", POOL_KEY, Strategy.RANDOM)

        assertThat(target.filterTokens(inputTokens, query)).containsOnly(token1, token2)
    }

    @Test
    fun `owner hash and tag should match token owner hash and tag`() {
        val query = ClaimQuery("r1", "f1", BigDecimal(1), "t2", "h1", POOL_KEY, Strategy.RANDOM)

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

package net.corda.ledger.utxo.token.cache.impl.entities

import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.ledger.utxo.token.cache.entities.internal.TokenCacheImpl
import net.corda.v5.ledger.utxo.token.selection.Strategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Duration

class TokenCacheImplTest {

    private lateinit var target: TokenCache

    @BeforeEach
    fun setup() {
        target = TokenCacheImpl(Duration.ZERO)
    }

    @Test
    fun `adding a token`() {
        val cachedToken = mock<CachedToken>()
        target.add(listOf(cachedToken), Strategy.RANDOM)
        assertThat(target.get(Strategy.RANDOM).toList()).containsOnly(cachedToken)
    }

    @Test
    fun `replace a token`() {
        val cachedToken1 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s1") }
        val cachedToken2 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s1") }
        target.add(listOf(cachedToken1), Strategy.RANDOM)

        assertThat(target.get(Strategy.RANDOM).toList()).containsOnly(cachedToken1)

        target.add(listOf(cachedToken2), Strategy.RANDOM)

        assertThat(target.get(Strategy.RANDOM).toList()).containsOnly(cachedToken2)
    }

    @Test
    fun `removing a token`() {
        val cachedToken1 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s1") }
        val cachedToken2 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s2") }
        val cachedToken3 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s3") }
        target.add(listOf(cachedToken1, cachedToken2, cachedToken3), Strategy.RANDOM)

        assertThat(target.get(Strategy.RANDOM).toList()).containsOnly(cachedToken1, cachedToken2, cachedToken3)
        target.removeAll(setOf("s1", "s3"))
        assertThat(target.get(Strategy.RANDOM).toList()).containsOnly(cachedToken2)
    }

    @Test
    fun `remove all`() {
        val cachedToken1 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s1") }
        val cachedToken2 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s2") }
        val cachedToken3 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s3") }
        target.add(listOf(cachedToken1, cachedToken2, cachedToken3), Strategy.RANDOM)

        assertThat(target.get(Strategy.RANDOM).toList()).containsOnly(cachedToken1, cachedToken2, cachedToken3)
        target.removeAll()
        assertThat(target.get(Strategy.RANDOM).toList()).isEmpty()
    }

    @Test
    fun `iterating the cache`() {
        val cachedToken1 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s1") }
        val cachedToken2 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s2") }
        val cachedToken3 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s3") }
        target.add(listOf(cachedToken1, cachedToken2, cachedToken3), Strategy.RANDOM)

        assertThat(target.get(Strategy.RANDOM).toList()).containsOnly(cachedToken1, cachedToken2, cachedToken3)
    }
}

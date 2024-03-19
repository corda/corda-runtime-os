package net.corda.ledger.utxo.token.cache.impl.entities

import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.internal.TokenCacheImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TokenCacheImplTest {

    private val expiryPeriod = 0L

    @Test
    fun `adding a token`() {
        val target = TokenCacheImpl()
        val cachedToken = mock<CachedToken>()
        target.add(listOf(cachedToken), expiryPeriod)
        assertThat(target.toList()).containsOnly(cachedToken)
    }

    @Test
    fun `replace a token`() {
        val target = TokenCacheImpl()
        val cachedToken1 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s1") }
        val cachedToken2 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s1") }
        target.add(listOf(cachedToken1), expiryPeriod)

        assertThat(target.toList()).containsOnly(cachedToken1)

        target.add(listOf(cachedToken2), expiryPeriod)

        assertThat(target.toList()).containsOnly(cachedToken2)
    }

    @Test
    fun `removing a token`() {
        val target = TokenCacheImpl()
        val cachedToken1 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s1") }
        val cachedToken2 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s2") }
        val cachedToken3 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s3") }
        target.add(listOf(cachedToken1, cachedToken2, cachedToken3), expiryPeriod)

        assertThat(target.toList()).containsOnly(cachedToken1, cachedToken2, cachedToken3)
        target.removeAll(setOf("s1", "s3"))
        assertThat(target.toList()).containsOnly(cachedToken2)
    }

    @Test
    fun `remove all`() {
        val target = TokenCacheImpl()
        val cachedToken1 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s1") }
        val cachedToken2 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s2") }
        val cachedToken3 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s3") }
        target.add(listOf(cachedToken1, cachedToken2, cachedToken3), expiryPeriod)

        assertThat(target.toList()).containsOnly(cachedToken1, cachedToken2, cachedToken3)
        target.removeAll()
        assertThat(target.toList()).isEmpty()
    }

    @Test
    fun `iterating the cache`() {
        val target = TokenCacheImpl()
        val cachedToken1 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s1") }
        val cachedToken2 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s2") }
        val cachedToken3 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s3") }
        target.add(listOf(cachedToken1, cachedToken2, cachedToken3), expiryPeriod)

        assertThat(target.toList()).containsOnly(cachedToken1, cachedToken2, cachedToken3)
    }
}

package net.corda.ledger.utxo.token.cache.impl.entities

import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.ledger.utxo.token.cache.entities.internal.TokenCacheImpl
import net.corda.v5.ledger.utxo.token.selection.Strategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.lang.Thread.sleep
import java.time.Duration

class TokenCacheImplTest {

    private lateinit var target: TokenCache

    @BeforeEach
    fun setup() {
        target = TokenCacheImpl(Duration.ofSeconds(10))
    }


    @ParameterizedTest
    @EnumSource(Strategy::class)
    fun `adding a token`(strategy: Strategy) {
        val cachedToken = mock<CachedToken>()
        target.add(listOf(cachedToken), strategy)
        assertThat(target.get(strategy).toList()).containsOnly(cachedToken)
    }

    @ParameterizedTest
    @EnumSource(Strategy::class)
    fun `replace a token`(strategy: Strategy) {
        val cachedToken1 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s1") }
        val cachedToken2 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s1") }
        target.add(listOf(cachedToken1), strategy)

        assertThat(target.get(strategy).toList()).containsOnly(cachedToken1)

        target.add(listOf(cachedToken2), strategy)

        assertThat(target.get(strategy).toList()).containsOnly(cachedToken2)
    }

    @ParameterizedTest
    @EnumSource(Strategy::class)
    fun `removing a token`(strategy: Strategy) {
        val cachedToken1 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s1") }
        val cachedToken2 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s2") }
        val cachedToken3 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s3") }
        target.add(listOf(cachedToken1, cachedToken2, cachedToken3), strategy)

        assertThat(target.get(strategy).toList()).containsOnly(cachedToken1, cachedToken2, cachedToken3)
        target.removeAll(setOf("s1", "s3"))
        assertThat(target.get(strategy).toList()).containsOnly(cachedToken2)
    }

    @ParameterizedTest
    @EnumSource(Strategy::class)
    fun `remove all`(strategy: Strategy) {
        val cachedToken1 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s1") }
        val cachedToken2 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s2") }
        val cachedToken3 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s3") }
        target.add(listOf(cachedToken1, cachedToken2, cachedToken3), strategy)

        assertThat(target.get(strategy).toList()).containsOnly(cachedToken1, cachedToken2, cachedToken3)
        target.removeAll()
        assertThat(target.get(strategy).toList()).isEmpty()
    }

    @ParameterizedTest
    @EnumSource(Strategy::class)
    fun `iterating the cache`(strategy: Strategy) {
        val cachedToken1 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s1") }
        val cachedToken2 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s2") }
        val cachedToken3 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s3") }
        target.add(listOf(cachedToken1, cachedToken2, cachedToken3), strategy)

        assertThat(target.get(strategy).toList()).containsOnly(cachedToken1, cachedToken2, cachedToken3)
    }

    @Test
    fun `no tokens are returned if different strategy is used`() {
        val cachedToken1 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s1") }
        val cachedToken2 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s2") }
        val cachedToken3 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s3") }
        target.add(listOf(cachedToken1, cachedToken2, cachedToken3), Strategy.RANDOM)

        assertThat(target.get(Strategy.RANDOM).toList()).containsOnly(cachedToken1, cachedToken2, cachedToken3)
        assertThat(target.get(Strategy.PRIORITY).toList()).isEmpty()
    }

    @Test
    fun `ensure the expiry period is respected`() {
        val expiryPeriodInMillis = 1000L
        val strategy = Strategy.PRIORITY

        val target = TokenCacheImpl(Duration.ofMillis(expiryPeriodInMillis))
        val cachedToken1 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s1") }
        val cachedToken2 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s2") }
        val cachedToken3 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s3") }
        target.add(listOf(cachedToken1, cachedToken2, cachedToken3), strategy)
        assertThat(target.get(strategy).toList()).containsOnly(cachedToken1, cachedToken2, cachedToken3)

        sleep(expiryPeriodInMillis)
        assertThat(target.get(strategy).toList()).isEmpty()
    }

    @Test
    fun `ensure the expiry period is not applied if the random strategy is used`() {
        val expiryPeriodInMillis = 5L
        val strategy = Strategy.RANDOM

        val target = TokenCacheImpl(Duration.ofMillis(expiryPeriodInMillis))
        val cachedToken1 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s1") }
        val cachedToken2 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s2") }
        val cachedToken3 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s3") }
        target.add(listOf(cachedToken1, cachedToken2, cachedToken3), strategy)
        assertThat(target.get(strategy).toList()).containsOnly(cachedToken1, cachedToken2, cachedToken3)
        sleep(expiryPeriodInMillis)
        assertThat(target.get(strategy).toList()).containsOnly(cachedToken1, cachedToken2, cachedToken3)
    }

    @Test
    fun `ensure the expiry time gets reset when the cache is updated`() {
        val expiryPeriodInMillis = 1000L
        val strategy = Strategy.PRIORITY

        val target = TokenCacheImpl(Duration.ofMillis(expiryPeriodInMillis))
        val cachedToken1 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s1") }
        val cachedToken2 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s2") }
        val cachedToken3 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s3") }
        target.add(listOf(cachedToken1, cachedToken2, cachedToken3), strategy)
        assertThat(target.get(strategy).toList()).containsOnly(cachedToken1, cachedToken2, cachedToken3)
        sleep(expiryPeriodInMillis/2)
        target.add(listOf(cachedToken1, cachedToken2, cachedToken3), strategy)
        sleep(expiryPeriodInMillis/2)
        assertThat(target.get(strategy).toList()).containsOnly(cachedToken1, cachedToken2, cachedToken3)
        sleep(expiryPeriodInMillis)
        assertThat(target.get(strategy).toList()).isEmpty()
    }
}

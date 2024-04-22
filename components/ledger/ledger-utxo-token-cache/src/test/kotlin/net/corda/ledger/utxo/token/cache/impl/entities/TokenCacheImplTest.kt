package net.corda.ledger.utxo.token.cache.impl.entities

import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.ledger.utxo.token.cache.entities.internal.TokenCacheImpl
import net.corda.test.util.time.AutoTickTestClock
import net.corda.utilities.millis
import net.corda.utilities.seconds
import net.corda.v5.ledger.utxo.token.selection.Strategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class TokenCacheImplTest {

    private lateinit var target: TokenCache
    private val cachedToken1 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s1") }
    private val cachedToken2 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s2") }
    private val cachedToken3 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s3") }

    @BeforeEach
    fun setup() {
        target = TokenCacheImpl(10.seconds, AutoTickTestClock(Instant.EPOCH, 1.seconds))
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
        target.add(listOf(cachedToken1, cachedToken2, cachedToken3), strategy)
        assertThat(target.get(strategy).toList()).containsOnly(cachedToken1, cachedToken2, cachedToken3)

        target.removeAll(setOf("s1", "s3"))
        assertThat(target.get(strategy).toList()).containsOnly(cachedToken2)
    }

    @ParameterizedTest
    @EnumSource(Strategy::class)
    fun `remove all`(strategy: Strategy) {
        target.add(listOf(cachedToken1, cachedToken2, cachedToken3), strategy)
        assertThat(target.get(strategy).toList()).containsOnly(cachedToken1, cachedToken2, cachedToken3)

        target.removeAll()
        assertThat(target.get(strategy).toList()).isEmpty()
    }

    @ParameterizedTest
    @EnumSource(Strategy::class)
    fun `iterating the cache`(strategy: Strategy) {
        target.add(listOf(cachedToken1, cachedToken2, cachedToken3), strategy)
        assertThat(target.get(strategy).toList()).containsOnly(cachedToken1, cachedToken2, cachedToken3)
    }

    @Test
    fun `no tokens are returned if different strategy is used`() {
        target.add(listOf(cachedToken1, cachedToken2, cachedToken3), Strategy.RANDOM)

        assertThat(target.get(Strategy.RANDOM).toList()).containsOnly(cachedToken1, cachedToken2, cachedToken3)
        assertThat(target.get(Strategy.PRIORITY).toList()).isEmpty()
    }

    @Test
    fun `ensure the expiry period is respected`() {
        val strategy = Strategy.PRIORITY

        val target = TokenCacheImpl(2.seconds, AutoTickTestClock(Instant.EPOCH, 1500.millis))
        target.add(listOf(cachedToken1, cachedToken2, cachedToken3), strategy)
        assertThat(target.get(strategy).toList()).containsOnly(cachedToken1, cachedToken2, cachedToken3)
        assertThat(target.get(strategy).toList()).isEmpty()
    }

    @Test
    fun `ensure the expiry period is not applied if the random strategy is used`() {
        val strategy = Strategy.RANDOM

        val target = TokenCacheImpl(1.seconds, AutoTickTestClock(Instant.EPOCH, 2.seconds))
        target.add(listOf(cachedToken1, cachedToken2, cachedToken3), strategy)
        assertThat(target.get(strategy).toList()).containsOnly(cachedToken1, cachedToken2, cachedToken3)
    }

    @Test
    fun `ensure the expiry time gets reset when the cache is updated`() {
        val strategy = Strategy.PRIORITY

        val target = TokenCacheImpl(1.seconds, AutoTickTestClock(Instant.EPOCH, 800.millis))
        target.add(listOf(cachedToken1, cachedToken2, cachedToken3), strategy)
        assertThat(target.get(strategy).toList()).containsOnly(cachedToken1, cachedToken2, cachedToken3)
        target.add(listOf(cachedToken1, cachedToken2, cachedToken3), strategy)
        assertThat(target.get(strategy).toList()).containsOnly(cachedToken1, cachedToken2, cachedToken3)
        assertThat(target.get(strategy).toList()).isEmpty()
    }
}

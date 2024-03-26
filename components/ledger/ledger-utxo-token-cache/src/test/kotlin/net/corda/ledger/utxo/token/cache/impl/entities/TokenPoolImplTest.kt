// package net.corda.ledger.utxo.token.cache.impl.entities
//
// import net.corda.ledger.utxo.token.cache.entities.CachedToken
// import net.corda.ledger.utxo.token.cache.entities.internal.TokenPoolCacheImpl
// import net.corda.ledger.utxo.token.cache.impl.POOL_KEY
// import net.corda.utilities.millis
// import net.corda.v5.ledger.utxo.token.selection.Strategy
// import org.assertj.core.api.Assertions
// import org.junit.jupiter.api.Test
// import org.mockito.kotlin.mock
// import org.mockito.kotlin.whenever
// import java.math.BigDecimal
// import java.time.Duration
//
// // Revise this test
// class TokenPoolImplTest {
//
//    private val cachedToken = mock<CachedToken> {
//        whenever(it.stateRef).thenReturn("stateRef1")
//        whenever(it.amount).thenReturn(BigDecimal(1))
//    }
//
//    @Test
//    fun `ensure the cache can be updated`() {
//        // Cache will never expire
//        val tokenPoolCache = TokenPoolCacheImpl(Duration.ZERO)
//        val cache = tokenPoolCache.get(POOL_KEY)
//        Assertions.assertThat(cache.get(Strategy.RANDOM)).isEmpty()
//
//        // Update the cache
//        cache.add(listOf(cachedToken), Strategy.RANDOM)
//
//        Assertions.assertThat(tokenPoolCache.get(POOL_KEY)).isNotEmpty
//    }
//
//    @Test
//    fun `ensure the cache expires after the expiry period`() {
//        // Cache will expire after two hundred millisecond
//        val expiryPeriod = 200.millis
//
//        val tokenPoolCache = TokenPoolCacheImpl(expiryPeriod)
//        val cache = tokenPoolCache.get(POOL_KEY)
//
//        // Update the cache
//        cache.add(listOf(cachedToken))
//        tokenPoolCache.put(POOL_KEY, cache)
//        Assertions.assertThat(tokenPoolCache.get(POOL_KEY)).isNotEmpty()
//
//        // Wait until the expiry period is over
//        Thread.sleep(expiryPeriod.toMillis())
//
//        // Ensure there are no cached tokens
//        Assertions.assertThat(tokenPoolCache.get(POOL_KEY)).isEmpty()
//    }
//
//    @Test
//    fun `ensure the expiry period is refreshed after an update`() {
//        // Cache will expire after two hundred millisecond
//        val expiryPeriod = 200.millis
//
//        val tokenPoolCache = TokenPoolCacheImpl(expiryPeriod)
//        val cache = tokenPoolCache.get(POOL_KEY)
//
//        // Update the cache
//        cache.add(listOf(cachedToken))
//        tokenPoolCache.put(POOL_KEY, cache)
//        Assertions.assertThat(cache).isNotEmpty()
//
//        // Wait until half the expiry period
//        Thread.sleep(expiryPeriod.toMillis() / 2)
//
//        // Update the cache again
//        cache.add(listOf(cachedToken))
//        tokenPoolCache.put(POOL_KEY, cache)
//
//        // Wait another half
//        Thread.sleep(expiryPeriod.toMillis() / 2)
//
//        // The cache must still have the tokens due to the second update
//        Assertions.assertThat(tokenPoolCache.get(POOL_KEY)).isNotEmpty()
//    }
//
//    @Test
//    fun `ensure the expiry period is applied to different caches`() {
//        val expiryPeriod = 1000.millis
//        val tokenPoolCache = TokenPoolCacheImpl(expiryPeriod)
//
//        // Add token to cache
//        val cache = tokenPoolCache.get(POOL_KEY)
//        cache.add(listOf(cachedToken))
//        tokenPoolCache.put(POOL_KEY, cache)
//        Assertions.assertThat(tokenPoolCache.get(POOL_KEY)).isNotEmpty()
//
//        // Wait half the time of expiry period
//        Thread.sleep(expiryPeriod.toMillis() / 2)
//
//        val poolKeyH2 = POOL_KEY.copy(shortHolderId = "h2")
//        val cacheH2 = tokenPoolCache.get(poolKeyH2)
//        Assertions.assertThat(cacheH2).isEmpty()
//
//        // Add token to different cache
//        cacheH2.add(listOf(cachedToken))
//        tokenPoolCache.put(poolKeyH2, cache)
//        Assertions.assertThat(tokenPoolCache.get(poolKeyH2)).isNotEmpty()
//
//        // Wait until the first cache expires but not the second
//        Thread.sleep(expiryPeriod.toMillis() / 2)
//
//        // Ensure there are no cached tokens in the first cache
//        Assertions.assertThat(tokenPoolCache.get(POOL_KEY)).isEmpty()
//        // Ensure there are tokens on the second cache
//        Assertions.assertThat(tokenPoolCache.get(poolKeyH2)).isNotEmpty()
//
//        // Wait until the second cache expires too
//        Thread.sleep(expiryPeriod.toMillis())
//
//        // Ensure there are no tokens on the second cache
//        Assertions.assertThat(tokenPoolCache.get(poolKeyH2)).isEmpty()
//    }
// }

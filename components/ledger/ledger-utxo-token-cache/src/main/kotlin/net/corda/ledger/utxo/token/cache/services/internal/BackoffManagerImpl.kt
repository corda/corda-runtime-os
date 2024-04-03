package net.corda.ledger.utxo.token.cache.services.internal

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
import net.corda.ledger.utxo.token.cache.services.BackoffManager
import net.corda.utilities.retry.Exponential
import net.corda.utilities.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.math.min

class BackoffManagerImpl(
    private val clock: Clock,
    private val dbTokensFetchMinIntervalInMillis: Long,
    private val dbTokensFetchMaxIntervalInMillis: Long
) : BackoffManager {

    inner class AttemptAndBackoffTimePair(val counter: Int, val backoffTime: Instant)

    val backoffStrategy = Exponential(base = 2.0, growthFactor = dbTokensFetchMinIntervalInMillis)

    val cache: Cache<TokenPoolKey, AttemptAndBackoffTimePair> = CacheFactoryImpl().build(
        "token-claim-backoff-map",
        Caffeine.newBuilder().expireAfterWrite(Duration.ofMillis(dbTokensFetchMaxIntervalInMillis))
    )

    override fun update(poolKey: TokenPoolKey) {
        val counterAndBackoffTimePair = cache.get(poolKey) { AttemptAndBackoffTimePair(-1, clock.instant()) }
        val attempt = counterAndBackoffTimePair.counter + 1
        val delayInMillis = min(backoffStrategy.delay(attempt), dbTokensFetchMaxIntervalInMillis)
        val backoffTime = clock.instant().plus(Duration.ofMillis(delayInMillis))

        cache.put(poolKey, AttemptAndBackoffTimePair(attempt, backoffTime))
    }

    override fun backoff(poolKey: TokenPoolKey): Boolean {
        val counterAndBackoffTimePair = cache.getIfPresent(poolKey) ?: return false
        return clock.instant() <= counterAndBackoffTimePair.backoffTime
    }
}

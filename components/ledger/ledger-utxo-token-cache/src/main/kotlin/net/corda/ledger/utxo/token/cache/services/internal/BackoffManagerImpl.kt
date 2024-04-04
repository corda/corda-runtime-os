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
    private dbBackoffMinPeriod: Duration,
    private val dbBackoffMaxPeriod: Duration
) : BackoffManager {

    inner class AttemptAndBackoffTimePair(val attempt: Int, val backoffTime: Instant)

    private val backoffStrategy = Exponential(base = 2.0, growthFactor = dbBackoffMinPeriod.toMillis())

    val cache: Cache<TokenPoolKey, AttemptAndBackoffTimePair> = CacheFactoryImpl().build(
        "token-claim-db-backoff-map",
        Caffeine.newBuilder().expireAfterWrite(dbBackoffMaxPeriod)
    )

    override fun update(poolKey: TokenPoolKey) {
        val attemptAndBackoffTimePair = cache.get(poolKey) { AttemptAndBackoffTimePair(-1, clock.instant()) }
        val attempt = attemptAndBackoffTimePair.attempt + 1
        val delayInMillis = min(backoffStrategy.delay(attempt), dbBackoffMaxPeriod.toMillis())
        val backoffTime = clock.instant().plus(Duration.ofMillis(delayInMillis))

        cache.put(poolKey, AttemptAndBackoffTimePair(attempt, backoffTime))
    }

    override fun backoff(poolKey: TokenPoolKey): Boolean {
        val attemptAndBackoffTimePair = cache.getIfPresent(poolKey) ?: return false
        return clock.instant() <= attemptAndBackoffTimePair.backoffTime
    }
}

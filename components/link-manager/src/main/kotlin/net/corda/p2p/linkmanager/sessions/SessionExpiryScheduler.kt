package net.corda.p2p.linkmanager.sessions

import com.github.benmanes.caffeine.cache.Cache
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.p2p.linkmanager.sessions.metadata.CommonMetadata.Companion.toCommonMetadata
import net.corda.utilities.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

internal class SessionExpiryScheduler(
    private val caches: Collection<Cache<String, *>>,
    private val stateManager: StateManager,
    private val clock: Clock,
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
    private val noiseFactory: Random = Random(),
) {
    private val tasks = ConcurrentHashMap<String, SavedState>()

    private data class SavedState(
        val expiry: Instant,
        val future: Future<*>,
    )

    fun checkStateValidateAndRememberIt(state: State): State? {
        val now = clock.instant()
        val expiry = state.metadata.toCommonMetadata().expiry
        val noise = Duration.of(
            noiseFactory.nextLong(20 * 60),
            TimeUnit.SECONDS.toChronoUnit(),
        )
        val duration = Duration.between(now, expiry) - noise
        return if (duration.isNegative) {
            forgetState(state)
            null
        } else {
            tasks.compute(state.key) { _, currentValue ->
                if (currentValue?.expiry == expiry) {
                    currentValue
                } else {
                    currentValue?.future?.cancel(false)
                    SavedState(
                        expiry = expiry,
                        future = scheduler.schedule({ forgetState(state) }, duration.toMillis(), TimeUnit.MILLISECONDS),
                    )
                }
            }
            state
        }
    }
    fun checkStatesValidateAndRememberThem(states: Map<String, State>): Map<String, State> {
        return states.mapNotNull { (key, state) ->
            checkStateValidateAndRememberIt(state)?.let {
                key to it
            }
        }.toMap()
    }

    fun forgetState(state: State) {
        val key = state.key
        stateManager.delete(listOf(state))
        caches.forEach {
            it.invalidate(key)
        }
        tasks[key]?.future?.cancel(false)
        tasks.remove(key)
    }
}

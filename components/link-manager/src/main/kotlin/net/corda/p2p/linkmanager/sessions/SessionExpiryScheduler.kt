package net.corda.p2p.linkmanager.sessions

import com.github.benmanes.caffeine.cache.Cache
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.p2p.linkmanager.sessions.metadata.CommonMetadata.Companion.toCommonMetadata
import net.corda.utilities.time.Clock
import org.slf4j.LoggerFactory
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
    private companion object {
        val logger = LoggerFactory.getLogger(SessionExpiryScheduler::class.java)
    }
    private val tasks = ConcurrentHashMap<String, SavedState>()

    private data class SavedState(
        val expiry: Instant,
        val version: Int,
        val future: Future<*>,
    )

    fun checkStateValidateAndRememberIt(
        state: State,
        beforeUpdate: Boolean = false,
    ): State? {
        val now = clock.instant()
        val expiry = state.metadata.toCommonMetadata().expiry
        val noise = Duration.of(
            noiseFactory.nextLong(20 * 60),
            TimeUnit.SECONDS.toChronoUnit(),
        )
        val stateToForget = if (beforeUpdate) {
            state.copy(
                version = state.version + 1,
            )
        } else {
            state
        }
        val duration = Duration.between(now, expiry) - noise
        return if (duration.isNegative) {
            forgetState(stateToForget)
            null
        } else {
            tasks.compute(state.key) { _, currentValue ->
                if ((currentValue?.expiry == expiry) && (currentValue.version == stateToForget.version)) {
                    currentValue
                } else {
                    currentValue?.future?.cancel(false)
                    SavedState(
                        expiry = expiry,
                        version = stateToForget.version,
                        future = scheduler.schedule({
                            forgetState(stateToForget)
                        }, duration.toMillis(), TimeUnit.MILLISECONDS),
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


    private fun forgetState(state: State) {
        val key = state.key
        val failedDeleted = stateManager.delete(listOf(state))
        if (failedDeleted.isNotEmpty()) {
            logger.info("Failed to delete state: ${failedDeleted.keys.first()}, trying to delete the latest version")
            stateManager.delete(failedDeleted.values)
        }
        caches.forEach {
            it.invalidate(key)
        }
        tasks.remove(key)
    }
}

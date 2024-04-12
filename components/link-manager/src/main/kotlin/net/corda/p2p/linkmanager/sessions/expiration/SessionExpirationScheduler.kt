package net.corda.p2p.linkmanager.sessions.expiration

import net.corda.libs.statemanager.api.State
import net.corda.p2p.linkmanager.sessions.SessionCache
import net.corda.p2p.linkmanager.sessions.SessionCache.SavedState
import net.corda.p2p.linkmanager.sessions.metadata.CommonMetadata.Companion.toCommonMetadata
import net.corda.utilities.time.Clock
import java.time.Duration
import java.util.Random
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Validates the states and schedules expiry time for them.
 * When the session gets expired it will get evicted from the [SessionCache] and [StateManager] database.
 */
internal class SessionExpirationScheduler(
    private val clock: Clock,
    private val sessionCache: SessionCache,
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
    private val noiseFactory: Random = Random(),
) {
    fun validateStatesAndScheduleExpiry(states: Map<String, State>): Map<String, State> {
        return states.mapNotNull { (key, state) ->
            validateStateAndScheduleExpiry(state)?.let {
                key to it
            }
        }.toMap()
    }

    fun validateStateAndScheduleExpiry(
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
            sessionCache.forgetState(stateToForget)
            null
        } else {
            val currentValue = sessionCache.retrieveCurrentlyScheduledTask(state.key)
            if ((currentValue?.expiry == expiry) && (currentValue.version == stateToForget.version)) {
                sessionCache.cacheScheduledTask(state.key, currentValue)
            } else {
                sessionCache.cancelCurrentlyScheduledTask(currentValue)
                val newValue = SavedState(
                    expiry = expiry,
                    version = stateToForget.version,
                    future = scheduler.schedule(
                        {
                            sessionCache.forgetState(stateToForget)
                        },
                        duration.toMillis(),
                        TimeUnit.MILLISECONDS,
                    ),
                )
                sessionCache.cacheScheduledTask(state.key, newValue)
            }
            state
        }
    }
}
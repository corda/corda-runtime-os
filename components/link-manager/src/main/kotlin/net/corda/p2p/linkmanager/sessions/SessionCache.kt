package net.corda.p2p.linkmanager.sessions

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.p2p.linkmanager.sessions.events.StatefulSessionEventPublisher
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

internal class SessionCache(
    private val stateManager: StateManager,
    private val clock: Clock,
    private val eventPublisher: StatefulSessionEventPublisher,
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
    private val noiseFactory: Random = Random(),
) {
    private companion object {
        const val CACHE_SIZE = 10_000L
        val logger = LoggerFactory.getLogger(SessionCache::class.java)
    }
    private val tasks = ConcurrentHashMap<String, SavedState>()

    private data class SavedState(
        val expiry: Instant,
        val version: Int,
        val future: Future<*>,
    )

    private val cachedInboundSessions: Cache<String, SessionManager.SessionDirection.Inbound> =
        CacheFactoryImpl().build(
            "P2P-inbound-sessions-cache",
            Caffeine.newBuilder().maximumSize(CACHE_SIZE).evictionListener { key, _, _ ->
                key?.let { removeFromScheduler(it) }
            },
        )

    private val counterpartiesForSessionId = ConcurrentHashMap<String, String>()

    private val cachedOutboundSessions: Cache<String, SessionManager.SessionDirection.Outbound> =
        CacheFactoryImpl().build(
            "P2P-outbound-sessions-cache",
            Caffeine.newBuilder()
                .maximumSize(CACHE_SIZE)
                .removalListener<String?, SessionManager.SessionDirection.Outbound?> { _, session, _ ->
                    session?.session?.let {
                        counterpartiesForSessionId.remove(it.sessionId)
                    }
                }.evictionListener { key, _, _ ->
                    key?.let { removeFromScheduler(it) }
                },
        )

    fun putOutboundSession(key: String, outboundSession: SessionManager.SessionDirection.Outbound) {
        cachedOutboundSessions.put(key, outboundSession)
        counterpartiesForSessionId[outboundSession.session.sessionId] = key
    }

    fun getAllPresentOutboundSessions(keys: Iterable<String>): Map<String, SessionManager.SessionDirection.Outbound> {
        return cachedOutboundSessions.getAllPresent(keys)
    }

    fun getBySessionIfCached(sessionID: String): SessionManager.SessionDirection? =
        cachedInboundSessions.getIfPresent(sessionID) ?: counterpartiesForSessionId[sessionID]?.let {
            cachedOutboundSessions.getIfPresent(it)
        }

    fun getKeyForOutboundSessionId(sessionID: String) = counterpartiesForSessionId[sessionID]

    fun getByKeyIfCached(key: String): SessionManager.SessionDirection? =
        cachedInboundSessions.getIfPresent(key) ?: cachedOutboundSessions.getIfPresent(key)

    fun putInboundSession(inboundSession: SessionManager.SessionDirection.Inbound) {
        cachedInboundSessions.put(inboundSession.session.sessionId, inboundSession)
    }

    fun invalidateAndRemoveFromSchedular(key: String) {
        invalidate(key)
        removeFromScheduler(key)
    }

    private fun invalidate(key: String) {
        cachedInboundSessions.invalidate(key)
        cachedOutboundSessions.invalidate(key)
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
                        future = scheduler.schedule(
                            {
                                forgetState(stateToForget)
                            },
                            duration.toMillis(),
                            TimeUnit.MILLISECONDS,
                        ),
                    )
                }
            }
            state
        }
    }
    fun validateStatesAndScheduleExpiry(states: Map<String, State>): Map<String, State> {
        return states.mapNotNull { (key, state) ->
            validateStateAndScheduleExpiry(state)?.let {
                key to it
            }
        }.toMap()
    }

    private fun removeFromScheduler(key: String) {
        tasks.compute(key) { _, currentValue ->
            currentValue?.future?.cancel(false)
            null
        }
    }

    private fun forgetState(state: State) {
        val key = state.key
        val failedDeleted = stateManager.delete(listOf(state))
        if (failedDeleted.isNotEmpty()) {
            logger.info("Failed to delete state: ${failedDeleted.keys.first()}, trying to delete the latest version")
            stateManager.delete(failedDeleted.values)
        }
        invalidate(key)
        eventPublisher.sessionDeleted(key)
        tasks.remove(key)
    }
}

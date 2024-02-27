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

@Suppress("TooManyFunctions")
internal class SessionCache(
    private val stateManager: StateManager,
    private val clock: Clock,
    private val eventPublisher: StatefulSessionEventPublisher,
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
    private val noiseFactory: Random = Random(),
) {
    private companion object {
        const val CACHE_SIZE = 10_000L
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
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

    fun invalidateAndRemoveFromScheduler(key: String) {
        invalidate(key)
        removeFromScheduler(key)
    }

    fun deleteBySessionId(sessionId: String) {
        val key = counterpartiesForSessionId[sessionId]
        if (key == null) {
            logger.warn("Failed to find associated state key for session ID '$sessionId'")
            return
        }
        deleteByKey(key)
    }

    fun deleteByKey(key: String) {
        invalidateAndRemoveFromScheduler(key)
        deleteStateByKey(key)
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
        var stateToDelete = state
        val key = state.key
        var retryCount = 0
        var failedDeletes = mapOf<String, State>()
        do {
            try {
                failedDeletes = stateManager.delete(listOf(stateToDelete))
            } catch (e: Exception) {
                logger.error("Unexpected error while trying to delete a session from the state manager.", e)
            }
            if (failedDeletes.isNotEmpty()) {
                stateToDelete = failedDeletes.values.first()
            }
        } while (retryCount++ < 3 && failedDeletes.isNotEmpty())

        if (failedDeletes.isNotEmpty()) {
            logger.error("Failed to delete the state for key $key after $retryCount attempts.")
        }

        invalidate(key)
        eventPublisher.sessionDeleted(key)
        tasks.remove(key)
    }

    private fun deleteStateByKey(key: String) {
        try {
            val state = stateManager.get(listOf(key)).values.firstOrNull()
            if (state == null) {
                logger.warn("Failed to delete session state for '$key', state does not exist")
                return
            }
            forgetState(state)
        } catch (e: Exception) {
            logger.error("Unexpected error while trying to fetch session state for '$key'.", e)
        }
    }
}

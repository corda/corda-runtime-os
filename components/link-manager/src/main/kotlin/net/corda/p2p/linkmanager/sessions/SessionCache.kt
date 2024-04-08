package net.corda.p2p.linkmanager.sessions

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.Gauge
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.data.p2p.event.SessionDirection
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.Resource
import net.corda.metrics.CordaMetrics
import net.corda.metrics.CordaMetrics.Metric.EstimatedSessionCacheSize
import net.corda.p2p.linkmanager.metrics.recordP2PMetric
import net.corda.p2p.linkmanager.metrics.recordSessionTimeoutMetric
import net.corda.p2p.linkmanager.sessions.events.StatefulSessionEventPublisher
import net.corda.p2p.linkmanager.sessions.metadata.CommonMetadata.Companion.toCommonMetadata
import net.corda.p2p.linkmanager.state.direction
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
import java.util.concurrent.atomic.AtomicReference
import kotlin.jvm.optionals.getOrNull

@Suppress("TooManyFunctions")
internal class SessionCache(
    private val stateManager: StateManager,
    private val clock: Clock,
    private val eventPublisher: StatefulSessionEventPublisher,
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
    private val noiseFactory: Random = Random(),
): Resource {
    private companion object {
        val defaultCacheSize = SessionManagerImpl.CacheSizes()
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
            Caffeine.newBuilder().maximumSize(defaultCacheSize.inbound).evictionListener { key, _, _ ->
                key?.let { removeFromScheduler(it) }
            },
        )

    private val counterpartiesForOutboundSessionId = ConcurrentHashMap<String, String>()

    private val cachedOutboundSessions: Cache<String, SessionManager.SessionDirection.Outbound> =
        CacheFactoryImpl().build(
            "P2P-outbound-sessions-cache",
            Caffeine.newBuilder()
                .maximumSize(defaultCacheSize.outbound)
                .removalListener<String?, SessionManager.SessionDirection.Outbound?> { _, session, _ ->
                    session?.session?.let {
                        counterpartiesForOutboundSessionId.remove(it.sessionId)
                    }
                }.evictionListener { key, _, _ ->
                    key?.let { removeFromScheduler(it) }
                },
        )

    // These metrics must be removed on shutdown as the MeterRegistry holds references to their lambdas.
    private val outboundCacheSize = AtomicReference<Gauge>().also {
        it.set(
            EstimatedSessionCacheSize { cachedOutboundSessions.estimatedSize() }.builder()
                .withTag(CordaMetrics.Tag.SessionDirection, SessionDirection.OUTBOUND.toString()).build()
        )
    }
    private val inboundCacheSize = AtomicReference<Gauge>().also {
        it.set(
            EstimatedSessionCacheSize { cachedInboundSessions.estimatedSize() }.builder()
                .withTag(CordaMetrics.Tag.SessionDirection, SessionDirection.INBOUND.toString()).build()
        )
    }

    fun putOutboundSession(key: String, outboundSession: SessionManager.SessionDirection.Outbound) {
        cachedOutboundSessions.put(key, outboundSession)
        counterpartiesForOutboundSessionId[outboundSession.session.sessionId] = key
    }

    fun getAllPresentOutboundSessions(keys: Iterable<String>): Map<String, SessionManager.SessionDirection.Outbound> {
        return cachedOutboundSessions.getAllPresent(keys)
    }

    fun getBySessionIfCached(sessionID: String): SessionManager.SessionDirection? =
        cachedInboundSessions.getIfPresent(sessionID) ?: counterpartiesForOutboundSessionId[sessionID]?.let {
            cachedOutboundSessions.getIfPresent(it)
        }

    fun getKeyForOutboundSessionId(sessionID: String) = counterpartiesForOutboundSessionId[sessionID]

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
        val key = counterpartiesForOutboundSessionId[sessionId]
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

    fun forgetState(state: State) {
        var stateToDelete = state
        val key = state.key
        var retryCount = 0
        var failedDeletes = mapOf<String, State>()
        do {
            try {
                failedDeletes = stateManager.delete(listOf(stateToDelete))
                recordSessionTimeoutMetric(state.metadata.toCommonMetadata().source, stateToDelete.direction())
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
        recordP2PMetric(CordaMetrics.Metric.SessionDeletedCount, state.direction())
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

    fun updateCacheSizes(sizes: SessionManagerImpl.CacheSizes) {
        cachedInboundSessions.policy().eviction().getOrNull()?.maximum = sizes.inbound
        cachedOutboundSessions.policy().eviction().getOrNull()?.maximum = sizes.outbound
    }

    override fun close() {
        CordaMetrics.registry.remove(inboundCacheSize.get())
        CordaMetrics.registry.remove(outboundCacheSize.get())
    }
}

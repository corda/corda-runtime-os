package net.corda.p2p.linkmanager.tracker

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.data.p2p.app.AppMessage
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.schema.registry.deserialize
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

internal class DataMessageCache(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val stateManager: StateManager,
    private val schemaRegistry: AvroSchemaRegistry,
    private val config: DeliveryTrackerConfiguration,
) : LifecycleWithDominoTile, DeliveryTrackerConfiguration.ConfigurationChanged {
    private companion object {
        const val NAME = "DataMessageCache"
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    fun get(key: String): AppMessage? {
        if (failedCreates.isNotEmpty()) {
            messageCache.putAll(failedCreates)
            failedCreates.clear()
        }
        return messageCache.getIfPresent(key)?.message ?: stateManager.getIfPresent(key)
    }

    fun put(key: String, message: AppMessage, partitionAndOffset: PartitionAndOffset) {
        messageCache.put(key, TrackedMessage(message, partitionAndOffset))
        offsetToMessageId[partitionAndOffset] = key
        if (partitionAndOffset.offset > (partitionOffsetTracker[partitionAndOffset.partition] ?: -1)) {
            partitionOffsetTracker[partitionAndOffset.partition] = partitionAndOffset.offset
        }
        persistOlderCacheEntries()
    }

    fun invalidate(key: String) {
        when(messageCache.getIfPresent(key)) {
            null -> {
                logger.trace("Deleting delivery tracker entry from state manager for '{}'.", key)
                stateManager.deleteIfPresent(key)
            }
            else -> {
                logger.trace("Discarding cached delivery tracker entry for '{}'.", key)
                messageCache.invalidate(key)
            }
        }
    }

    override fun changed() {
        updateCacheSize(config.config.maxCacheSizeMegabytes)
    }

    override val dominoTile = ComplexDominoTile(
        componentName = NAME,
        coordinatorFactory = coordinatorFactory,
        dependentChildren = listOf(stateManager.name, config.dominoTile.coordinatorName),
        onStart = ::onStart,
    )

    private fun onStart() {
        config.lister(this)
    }

    internal data class PartitionAndOffset(val partition: Int, val offset: Long)

    private data class TrackedMessage(
        val message: AppMessage,
        val tracker: PartitionAndOffset,
    )

    /**
     * Spill-over map for temporarily storing evicted cache entries that fail to be persisted.
     */
    private val failedCreates = ConcurrentHashMap<String, TrackedMessage>()

    /**
     * Map of partition/offset information to message IDs.
     */
    private val offsetToMessageId = ConcurrentHashMap<PartitionAndOffset, String>()

    /**
     * Keeps track of the largest offset value per partition.
     */
    private val partitionOffsetTracker = ConcurrentHashMap<Int, Long>()

    private val messageCache: Cache<String, TrackedMessage> =
        CacheFactoryImpl().build(
            "P2P-delivery-tracker-cache",
            Caffeine.newBuilder()
                .maximumSize(config.config.maxCacheSizeMegabytes)
                .removalListener<String?, TrackedMessage?> { _, value, _ ->
                    value?.let { onRemoval(it) }
                }.evictionListener { key, value, _ ->
                    if (key != null && value != null) {
                        onEviction(key, value)
                    }
                }
        )

    private fun updateCacheSize(size: Long) {
        messageCache.policy().eviction().ifPresent {
            it.maximum = size
        }
    }

    private fun onEviction(key: String, value: TrackedMessage) {
        logger.trace("Handling cache eviction for message ID: '{}'", key)
        val failure = stateManager.put(mapOf(key to value.message))
        if (failure.isNotEmpty()) {
            failedCreates[key] = value
        }
        offsetToMessageId.remove(value.tracker)
    }

    private fun onRemoval(value: TrackedMessage) {
        val key = value.tracker
        logger.trace("Handling cache entry removal for message ID: '{}'", offsetToMessageId[key])
        offsetToMessageId.remove(key)
    }

    private fun StateManager.getIfPresent(key: String): AppMessage? {
        return try {
            get(setOf(key)).values.firstOrNull()?.let {
                schemaRegistry.deserialize<AppMessage>(ByteBuffer.wrap(it.value))
            }
        } catch (e: Exception) {
            logger.error("Unexpected error while trying to fetch message state for '{}'.", key, e)
            null
        }
    }

    private fun StateManager.deleteIfPresent(key: String) {
        get(setOf(key)).values.firstOrNull()?.let { state ->
            var stateToDelete = state
            var retryCount = 0
            var failedDeletes = mapOf<String, State>()
            do {
                try {
                    failedDeletes = delete(setOf(stateToDelete))
                } catch (e: Exception) {
                    logger.error("Unexpected error while trying to delete message state from the state manager.", e)
                }
                if (failedDeletes.isNotEmpty()) {
                    stateToDelete = failedDeletes.values.first()
                }
            } while (retryCount++ < 3 && failedDeletes.isNotEmpty())

            if (failedDeletes.isNotEmpty()) {
                logger.warn("Failed to delete the state for key '{}' after '{} attempts.", key, retryCount)
            }
        }
    }

    private fun StateManager.put(messages: Map<String, AppMessage>): Set<String> {
        val newStates = messages.mapValues {
            State(it.key, schemaRegistry.serialize(it.value).array())
        }
        var statesToCreate = newStates.values
        var retryCount = 0
        var failedCreates = setOf<String>()
        do {
            try {
                failedCreates = create(statesToCreate).also {
                    logger.warn("Failed to persist tracking state for messages with the following keys: '{}'.", it)
                }
            } catch (e: Exception) {
                logger.error("Unexpected error while trying to persist message state to the state manager.", e)
            }
            if (failedCreates.isNotEmpty()) {
                statesToCreate = newStates.filterKeys { failedCreates.contains(it) }.values
            }
        } while (retryCount++ < 3 && failedCreates.isNotEmpty())

        if (failedCreates.isNotEmpty()) {
            logger.warn("Failed to persist tracking state for message IDs: '{}' after '{}' attempts.", failedCreates, retryCount)
        }
        return failedCreates
    }

    private fun persistOlderCacheEntries() {
        val thresholds = partitionOffsetTracker.mapValues {
            it.value - config.config.maxCacheOffsetAge
        }
        val messagesToPersist = offsetToMessageId.filterKeys { it.offset < (thresholds[it.partition] ?: 0) }
            .mapNotNull { entry ->
                val messageId = entry.value
                messageCache.getIfPresent(messageId)?.message?.let {
                    messageId to it
                }
            }.toMap()
        if (messagesToPersist.isEmpty()) {
            return
        }
        logger.trace(
            "Messages with IDs: '{}' are older than the configured offset age and will be moved to the state manager.",
            messagesToPersist.keys
        )
        stateManager.put(messagesToPersist).also { failed ->
            messageCache.invalidateAll((messagesToPersist - failed).keys)
        }
    }
}

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
import net.corda.messaging.api.records.EventLogRecord
import net.corda.p2p.linkmanager.tracker.exception.DataMessageCacheException
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.schema.registry.deserialize
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

internal class DataMessageCache(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val stateManager: StateManager,
    private val schemaRegistry: AvroSchemaRegistry,
    private val config: DeliveryTrackerConfiguration,
) : LifecycleWithDominoTile, DeliveryTrackerConfiguration.ConfigurationChanged {
    private companion object {
        const val NAME = "DataMessageCache"
        const val MAX_RETRIES = 3
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    fun get(key: String): AppMessage? {
        if (failedCreates.get().isNotEmpty()) {
            messageCache.putAll(failedCreates.getAndSet(ConcurrentHashMap()))
        }
        return messageCache.getIfPresent(key)?.message ?: stateManager.getIfPresent(key)
    }

    fun put(record: EventLogRecord<String, AppMessage>) {
        val message = record.value ?: return
        messageCache.put(record.key, TrackedMessage(message, record.partition, record.offset))
        partitionOffsetTracker.compute(record.partition) { _, info ->
            if ((info == null) || (record.offset > info.latestOffset)) {
                val offsetToMessageId = info?.offsetToMessageId ?: ConcurrentHashMap<Long, String>()
                offsetToMessageId[record.offset] = record.key
                PartitionInfo(record.offset, offsetToMessageId)
            } else {
                info.offsetToMessageId[record.offset] = record.key
                info
            }
        }
        persistOlderCacheEntries()
    }

    fun invalidate(key: String) {
        when(messageCache.getIfPresent(key)) {
            null -> {
                logger.trace("Deleting delivery tracker entry from state manager for '{}'.", key)
                stateManager.deleteIfPresent(key, MAX_RETRIES).apply {
                    if (!this) {
                        logger.warn("Failed to delete tracking state for '{}' after '{}' attempts.", key, MAX_RETRIES)
                    }
                }
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

    private data class TrackedMessage(
        val message: AppMessage,
        val partition: Int,
        val offset: Long,
    )

    /**
     * Spill-over map for temporarily storing evicted cache entries that fail to be persisted.
     */
    private val failedCreates = AtomicReference(ConcurrentHashMap<String, TrackedMessage>())

    /**
     * Data class for storing the latest offset and an offset to message ID map for a partition.
     */
    private data class PartitionInfo(
        val latestOffset: Long,
        val offsetToMessageId: ConcurrentHashMap<Long, String>
    )

    /**
     * Map for tracking [PartitionInfo] for each partition.
     */
    private val partitionOffsetTracker = ConcurrentHashMap<Int, PartitionInfo>()

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

    private fun onEviction(messageId: String, trackedMessage: TrackedMessage) {
        logger.trace("Handling cache eviction for message ID: '{}'", messageId)
        val failed = stateManager.put(mapOf(messageId to trackedMessage.message), MAX_RETRIES)
        if (failed.isNotEmpty()) {
            logger.warn("Failed to persist tracking state for '{}' after '{}' attempts.", failed, MAX_RETRIES)
            failedCreates.get()[messageId] = trackedMessage
        }
        partitionOffsetTracker[trackedMessage.partition]?.offsetToMessageId?.remove(trackedMessage.offset)
    }

    private fun onRemoval(message: TrackedMessage) {
        val offsetToMessageId = partitionOffsetTracker[message.partition]?.offsetToMessageId ?: return
        logger.trace("Handling cache entry removal for message ID: '{}'", offsetToMessageId[message.offset])
        offsetToMessageId.remove(message.offset)
    }

    private fun StateManager.getIfPresent(key: String): AppMessage? {
        return try {
            get(setOf(key)).values.firstOrNull()?.let {
                schemaRegistry.deserialize<AppMessage>(ByteBuffer.wrap(it.value))
            }
        } catch (e: Exception) {
            logger.warn("Unexpected error while trying to fetch message state for '{}'.", key, e)
            null
        }
    }

    private fun StateManager.deleteIfPresent(key: String, remainingAttempts: Int): Boolean {
        if (remainingAttempts <= 0) {
            return false
        }
        val stateToDelete = get(setOf(key)).values.firstOrNull() ?: return true
        val failedDeletes = try {
            delete(setOf(stateToDelete))
        } catch (e: Exception) {
            logger.warn("Unexpected error while trying to delete message state from the state manager.", e)
            emptyMap()
        }
        return if (failedDeletes.isNotEmpty()) {
            deleteIfPresent(key, remainingAttempts - 1)
        } else {
            true
        }
    }

    private fun StateManager.put(messages: Map<String, AppMessage>, remainingAttempts: Int): Set<String> {
        if (remainingAttempts <= 0) {
            return messages.keys
        }
        val newStates = messages.mapValues {
            State(it.key, schemaRegistry.serialize(it.value).array())
        }
        val failedCreates = try {
            create(newStates.values)
        } catch (e: Exception) {
            val errorMessage = "Unexpected error while trying to persist message state to the state manager."
            logger.warn(errorMessage, e)
            dominoTile.setError(DataMessageCacheException("$errorMessage. Cause: ${e.message}"))
            emptySet()
        }
        return if (failedCreates.isNotEmpty()) {
            put(messages.filterKeys { failedCreates.contains(it) }, remainingAttempts - 1)
        } else {
            emptySet()
        }
    }

    private fun persistOlderCacheEntries() {
        val thresholds = partitionOffsetTracker.mapValues {
            it.value.latestOffset - config.config.maxCacheOffsetAge
        }
        val messagesToPersist = partitionOffsetTracker.flatMap { (partition, info) ->
            info.offsetToMessageId.filterKeys { it < (thresholds[partition] ?: 0) }
                .mapNotNull { (_, messageId) ->
                    messageCache.getIfPresent(messageId)?.message?.let {
                        messageId to it
                    }
                }
        }.toMap()
        if (messagesToPersist.isEmpty()) {
            return
        }
        logger.trace(
            "Messages with IDs: '{}' are older than the configured offset age and will be moved to the state manager.",
            messagesToPersist.keys
        )
        stateManager.put(messagesToPersist, MAX_RETRIES).also { failed ->
            logger.warn("Failed to persist tracking state for '{}' after '{}' attempts.", failed, MAX_RETRIES)
            messageCache.invalidateAll((messagesToPersist - failed).keys)
        }
    }
}

package net.corda.p2p.linkmanager.tracker

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.data.p2p.app.AppMessage
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.Resource
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.schema.registry.deserialize
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

class DataMessageCache(
    private val stateManager: StateManager,
    private val schemaRegistry: AvroSchemaRegistry,
) : Resource {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
//        const val KEY_PREFIX = "P2P-msg"
        // TODO Get from config (DeliveryTracker)
        val maxCacheSize = 1L
        val maxCacheOffsetAge = 1L
    }

    private data class TrackedMessage(
        val message: AppMessage,
        val offset: Long,
    )

    private val messageCache: Cache<MessageId, TrackedMessage> =
        CacheFactoryImpl().build(
            "P2P-delivery-tracker-cache",
            Caffeine.newBuilder()
                .maximumSize(maxCacheSize)
                .removalListener<MessageId?, TrackedMessage?> { _, value, _ ->
                    value?.let { offsetToMessageId.remove(value.offset) }
                }.evictionListener { key, value, _ ->
                    if (key != null && value != null) {
                        stateManager.put(key, value.message)
                        offsetToMessageId.remove(value.offset)
                    }
                }
        )

    private val offsetToMessageId = ConcurrentHashMap<Long, MessageId>()

    fun get(key: MessageId): AppMessage? {
        return messageCache.getIfPresent(key)?.message ?: stateManager.getIfPresent(key)
    }

    fun put(key: MessageId, message: AppMessage, offset: Long) {
        messageCache.put(key, TrackedMessage(message, offset))
        offsetToMessageId[offset] = key
        persistOlderCacheEntries()
    }

    fun invalidate(key: MessageId) {
        messageCache.getIfPresent(key)?.let {
            logger.trace("Discarding cached delivery tracker entry for '{}'.", key)
            messageCache.invalidate(key)
        } ?: {
            logger.trace("Deleting delivery tracker entry from state manager for '{}'.", key)
            stateManager.deleteIfPresent(key)
        }
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
                logger.error("Failed to delete the state for key '{}' after '{} attempts.", key, retryCount)
            }
        }
        // TODO refactor retry
    }

    private fun StateManager.put(id: MessageId, message: AppMessage) {
        val newState = State(id, schemaRegistry.serialize(message).array())
        create(setOf(newState)).forEach {
            logger.warn("Failed to persist message '{}' for tracking delivery.", it)
        }
        // TODO retry
    }

    private fun persistOlderCacheEntries() {
        val threshold = offsetToMessageId.keys.max() - maxCacheOffsetAge
        offsetToMessageId.filterKeys { it < threshold }.forEach { entry ->
            val messageId = entry.value
            messageCache.getIfPresent(messageId)?.message?.let {
                stateManager.put(messageId, it)
                messageCache.invalidate(messageId)
            } ?: offsetToMessageId.remove(entry.key)

        }
    }

//    private fun MessageId.toKey() = "$KEY_PREFIX$this"

    override fun close() {
    }
}

private typealias MessageId = String

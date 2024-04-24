package net.corda.p2p.linkmanager.tracker

import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.p2p.linkmanager.common.CommonComponents
import net.corda.p2p.linkmanager.tracker.exception.DataMessageCacheException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicLong

internal class DataMessageCache(
    private val commonComponents: CommonComponents,
    private val config: DeliveryTrackerConfiguration,
    private val onOffsetsToReadFromChanged: (partitionsToLastPersistedOffset: Collection<Pair<Int, Long>>) -> Unit,
) : LifecycleWithDominoTile {
    private companion object {
        const val NAME = "DataMessageCache"
        const val MAX_RETRIES = 3
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    fun get(key: String): AuthenticatedMessage? {
        return messageCache[key]?.message ?: commonComponents.stateManager.getIfPresent(key)?.message
    }

    fun put(messages: Collection<MessageRecord>) {
        val toCache = messages.associateBy { message ->
            message.message.header.messageId
        }
        if (toCache.isEmpty()) {
            return
        }
        messageCache.putAll(toCache)
        messages.groupBy {
            it.partition
        }.forEach { (partition, recordsInPartition) ->
            partitionOffsetTracker.compute(partition) { _, info ->
                val newInfo = info ?: PartitionInfo()
                newInfo.offsetToMessageId.putAll(
                    recordsInPartition.associate {
                        it.offset to it.message.header.messageId
                    },
                )
                newInfo
            }
        }
        persistOlderCacheEntries()
    }

    fun remove(key: String): MessageRecord? {
        val record = messageCache.remove(key)
        logger.info("YYY in remove($key) we have ${record?.offset}")
        return if (record == null) {
            logger.trace("Deleting delivery tracker entry from state manager for '{}'.", key)
            commonComponents.stateManager.deleteIfPresent(key)
        } else {
            logger.trace("Discarding cached delivery tracker entry for '{}'.", key)
            val partitionInfo = partitionOffsetTracker[record.partition]
            if (partitionInfo != null) {
                partitionInfo.offsetToMessageId.remove(record.offset)

                partitionInfo.readMessagesFromOffset.updateAndGet {
                    partitionInfo.offsetToMessageId.firstEntry()?.key ?: (record.offset + 1)
                }
                onOffsetsToReadFromChanged(listOf(record.partition to partitionInfo.readMessagesFromOffset.get()))
            }
            record
        }
    }

    override val dominoTile = ComplexDominoTile(
        componentName = NAME,
        coordinatorFactory = commonComponents.lifecycleCoordinatorFactory,
        dependentChildren = listOf(commonComponents.stateManager.name, config.dominoTile.coordinatorName),
    )

    /**
     * Data class for storing the latest offset and an offset to message ID map for a partition.
     */
    private data class PartitionInfo(
        val offsetToMessageId: ConcurrentSkipListMap<Long, String> = ConcurrentSkipListMap(),
        val readMessagesFromOffset: AtomicLong = AtomicLong(0),
    )

    /**
     * Map for tracking [PartitionInfo] for each partition.
     */
    private val partitionOffsetTracker = ConcurrentHashMap<Int, PartitionInfo>()

    private val messageCache = ConcurrentHashMap<String, MessageRecord>()

    private fun StateManager.getIfPresent(key: String): MessageRecord? {
        return try {
            get(setOf(key)).values.firstOrNull()?.let {
                MessageRecord.fromState(
                    it,
                    commonComponents.schemaRegistry,
                )
            }
        } catch (e: Exception) {
            logger.warn("Unexpected error while trying to fetch message state for '{}'.", key, e)
            null
        }
    }

    private fun StateManager.deleteIfPresent(key: String, remainingAttempts: Int = MAX_RETRIES): MessageRecord? {
        if (remainingAttempts <= 0) {
            logger.warn("Failed to delete tracking state for '{}' after '{}' attempts.", key, MAX_RETRIES)
            return null
        }
        logger.info("YYY Looking for key: $key")
        val stateToDelete = get(setOf(key)).values.firstOrNull() ?: return null
        logger.info("YYY Got it: ${stateToDelete.key}")
        val failedDeletes = try {
            delete(setOf(stateToDelete))
        } catch (e: Exception) {
            logger.warn("Unexpected error while trying to delete message state from the state manager.", e)
            emptyMap()
        }
        return if (failedDeletes.isNotEmpty()) {
            deleteIfPresent(key, remainingAttempts - 1)
        } else {
            MessageRecord.fromState(stateToDelete, commonComponents.schemaRegistry).also {
                logger.info("YYY returning record ${it?.partition}")
            }
        }
    }

    private fun StateManager.put(
        messages: Map<String, MessageRecord>,
    ) {
        val newStates = messages.mapValues {
            it.value.toState(commonComponents.schemaRegistry)
        }
        val failedCreates = try {
            create(newStates.values)
        } catch (e: Exception) {
            val errorMessage = "Unexpected error while trying to persist message state to the state manager."
            logger.warn(errorMessage, e)
            dominoTile.setError(DataMessageCacheException("$errorMessage. Cause: ${e.message}", e))
            emptyList()
        }

        if (failedCreates.isNotEmpty()) {
            logger.info("Failed to persist messages with IDs: $failedCreates")
        }
    }

    private fun persistOlderCacheEntries() {
        val messagesToPersist = partitionOffsetTracker.values.flatMap { info ->
            val lastKey = info.offsetToMessageId.lastEntry()?.key
            if (lastKey != null) {
                val threshold = lastKey - config.config.maxCacheOffsetAge
                val ids = info.offsetToMessageId.headMap(threshold)
                ids.values.mapNotNull { messageId ->
                    messageCache.remove(messageId)?.let {
                        messageId to it
                    }
                }.also {
                    ids.clear()
                    val oldestEntry = info.offsetToMessageId.firstEntry()?.key
                    if (oldestEntry != null) {
                        info.readMessagesFromOffset.set(oldestEntry)
                    }
                }
            } else {
                emptyList()
            }
        }.toMap()
        if (messagesToPersist.isEmpty()) {
            return
        }
        logger.trace(
            "Messages with IDs: '{}' are older than the configured offset age and will be moved to the state manager.",
            messagesToPersist.keys,
        )
        commonComponents.stateManager.put(messagesToPersist)
        onOffsetsToReadFromChanged(
            partitionOffsetTracker.map {
                it.key to it.value.readMessagesFromOffset.get()
            },
        )
    }
}

package net.corda.p2p.linkmanager.tracker

import net.corda.data.p2p.app.AppMessage
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.schema.registry.deserialize
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

internal class DataMessageStore(
    private val stateManager: StateManager,
    private val schemaRegistry: AvroSchemaRegistry,
) {
    private companion object {
        val logger = LoggerFactory.getLogger(DataMessageStore::class.java)
    }
    fun read(ids: Collection<String>): Collection<AppMessage> {
        if (ids.isEmpty()) {
            return emptyList()
        }
        val keys = ids.map {
            it.toKey()
        }
        return stateManager
            .get(keys)
            .values
            .map {
                schemaRegistry.deserialize(ByteBuffer.wrap(it.value))
            }
    }

    fun write(messages: Collection<AppMessage>) {
        val states = messages.mapNotNull { appMessage ->
            appMessage.toState()?.let {
                it.key to it
            }
        }.toMap()

        if (states.isEmpty()) {
            return
        }

        val failedToCreate = stateManager.create(states.values)

        if (failedToCreate.isNotEmpty()) {
            logger.warn("Failed to write messages $failedToCreate")
        }
    }

    fun delete(ids: Collection<String>) {
        if (ids.isEmpty()) {
            return
        }
        val keys = ids.map {
            it.toKey()
        }

        val states = stateManager.get(keys).values

        if (states.isNotEmpty()) {
            val failedToDelete = stateManager.delete(states)
            if (failedToDelete.isNotEmpty()) {
                throw DataMessageStoreException(
                    "Failed to delete messages ${failedToDelete.keys}",
                )
            }
        }
    }

    private fun AppMessage.toState(): State? {
        val key = this.id?.toKey() ?: return null
        val value = schemaRegistry.serialize(this)
        return State(
            key = key,
            value = value.array(),
        )
    }

    private fun String.toKey() = "P2P-msg:$this"
}

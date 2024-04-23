package net.corda.p2p.linkmanager.tracker

import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.schema.registry.deserialize
import java.nio.ByteBuffer

internal data class MessageRecord(
    val message: AuthenticatedMessage,
    val partition: Int,
    val offset: Long,
) {
    companion object {
        private const val OFFSET = "OFFSET"
        private const val PARTITION = "PARTITION"
        fun fromState(
            state: State,
            schemaRegistry: AvroSchemaRegistry,
        ): MessageRecord? {
            val message = schemaRegistry.deserialize<AuthenticatedMessage>(ByteBuffer.wrap(state.value))
            val offset = (state.metadata.get(OFFSET) as? Number)?.toLong() ?: return null
            val partition = (state.metadata.get(PARTITION) as? Number)?.toInt() ?: return null
            return MessageRecord(
                message,
                offset = offset,
                partition = partition,
            )
        }
    }

    fun toState(
        schemaRegistry: AvroSchemaRegistry,
    ) = State(
        key = message.header.messageId,
        value = schemaRegistry.serialize(message).array(),
        metadata = Metadata(
            mapOf(
                OFFSET to offset,
                PARTITION to partition,
            ),
        ),
    )
}

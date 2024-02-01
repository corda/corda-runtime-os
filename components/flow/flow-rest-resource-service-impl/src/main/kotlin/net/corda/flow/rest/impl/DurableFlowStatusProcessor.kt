package net.corda.flow.rest.impl

import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.flow.FlowKey
import net.corda.data.flow.output.FlowStatus
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record

const val HOLDING_IDENTITY_METADATA_KEY = "holdingIdentity"

/**
 * This class is responsible for processing batches of records associated with [FlowStatus] changes from the [flow.status] topic.
 * These status changes are then persisted to a dedicated [StateManager] instance.
 *
 * @property stateManager The [StateManager] instance used for managing state persistence.
 * @property serializer The [CordaAvroSerializer] used for serializing flow statuses.
 *
 * Implements [DurableProcessor] for processing [FlowKey] and [FlowStatus] records.
 */
class DurableFlowStatusProcessor(
    private val stateManager: StateManager,
    private val serializer: CordaAvroSerializer<Any>
) : DurableProcessor<FlowKey, FlowStatus> {
    override val keyClass: Class<FlowKey> get() = FlowKey::class.java
    override val valueClass: Class<FlowStatus> get() = FlowStatus::class.java
    override fun onNext(events: List<Record<FlowKey, FlowStatus>>): List<Record<*, *>> {
        val flowKeys = events.map { it.key.toString() }
        val existingStates = stateManager.get(flowKeys)
        val existingKeys = existingStates.keys.toSet()

        val (updatedStates, newStates) = events.mapNotNull { record ->
            val key = record.key.toString()
            val bytes = record.value?.let { serializer.serialize(it) } ?: return@mapNotNull null

            val state = existingStates[key]
            val metadata = state?.metadata.withHoldingIdentity(record.key.identity)

            state?.copy(value = bytes, metadata = metadata) ?: State(key, bytes, metadata = metadata)
        }.partition { it.key in existingKeys }

        stateManager.create(newStates)
        stateManager.update(updatedStates)

        return emptyList()
    }

    private fun Metadata?.withHoldingIdentity(holdingIdentity: HoldingIdentity): Metadata {
        val metadata = this?.toMutableMap() ?: mutableMapOf()
        metadata[HOLDING_IDENTITY_METADATA_KEY] = holdingIdentity.toString()
        return Metadata(metadata)
    }
}

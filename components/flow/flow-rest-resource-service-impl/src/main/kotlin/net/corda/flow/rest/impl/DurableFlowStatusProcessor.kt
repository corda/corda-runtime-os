package net.corda.flow.rest.impl

import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.flow.FlowKey
import net.corda.data.flow.output.FlowStatus
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record

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

            existingStates[key]?.copy(value = bytes) ?: State(key, bytes)
        }.partition { it.key in existingKeys }

        stateManager.create(newStates)
        stateManager.update(updatedStates)

        return emptyList()
    }
}

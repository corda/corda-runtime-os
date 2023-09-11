package net.corda.messaging.mediator

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.api.mediator.statemanager.Metadata
import net.corda.messaging.api.mediator.statemanager.State
import net.corda.messaging.api.mediator.statemanager.StateManager
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.utils.toRecord
import net.corda.v5.base.exceptions.CordaRuntimeException

@Suppress("LongParameterList")
class ProcessorTask<K: Any, S: Any, E: Any>(
    private val key: String,
    private val events: Collection<CordaConsumerRecord<K, E>>,
    private val processor: StateAndEventProcessor<K, S, E>,
    private val stateManager: StateManager,
    private val serializer: CordaAvroSerializer<Any>,
    private val stateDeserializer: CordaAvroDeserializer<S>,
) {

    var responseEvents = emptyList<Record<*, *>>()
        private set
    fun run() {
        val persistedState = stateManager.get(setOf(key))[key]

        var updatedState = persistedState?.value?.let { stateDeserializer.deserialize(it) }
        responseEvents = events.map { event ->
            val response = processor.onNext(updatedState, event.toRecord())
            response.updatedState?.let { updatedState = it }
            response.responseEvents
        }.flatten()

        val serializedState = serializer.serialize(updatedState!!)
            ?: throw CordaRuntimeException("Cannot serialize updated state: $updatedState")

        val newState = State(
            key,
            serializedState,
            (persistedState?.version ?: -1) + 1,
            persistedState?.metadata ?: Metadata()
        )
        stateManager.update(setOf(newState))
    }
}
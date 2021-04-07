package api.samples.producerconsumer.processor

import api.samples.producerconsumer.records.EventRecord
import api.samples.producerconsumer.records.StateRecord

interface ActorProcessor : Processor {
    /**
     * These could just be baked into the onNext by the impls
     * or could be added as steps in an abstract class base process method
     */
    fun getStateForEvent(eventRecord: EventRecord) : StateRecord
    fun updateState(stateRecord: StateRecord)
}
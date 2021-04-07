package api.samples.producerconsumer.processor.impl

import api.samples.producerconsumer.processor.ActorProcessor
import api.samples.producerconsumer.records.EventRecord
import api.samples.producerconsumer.records.StateRecord

class SimpleActorProcessor: ActorProcessor<String> {
    override fun onNext(eventRecord: EventRecord<String>) {
        println("ActorModelProcessor: I'm processing my next record ${eventRecord.key} from eventSource ${eventRecord.eventSource}")

        //get state
        val state = getStateForEvent(eventRecord)

        //processing logic
        state.value = "new value"

        //update state
        updateState(state)

    }

    override fun getStateForEvent(eventRecord: EventRecord<String>) : StateRecord<String> {
        println("ActorModelProcessor: Getting the state for event ${eventRecord.key}")

        //some impl specific lib/configService call to get state for event goes here
        val stateRecord = StateRecord("stateSource", eventRecord.key, "some event value")

        return stateRecord
    }

    override fun updateState(stateRecord: StateRecord<String>)  {
        println("ActorModelProcessor: Getting the state for event id ${stateRecord.key}  and value ${stateRecord.value}")
    }

    override fun onSuccess(eventRecord: EventRecord<String>) {
        println("ActorModelProcessor: No events currently available to process")
        println("SimpleProcessor: Finished processing this ${eventRecord.key}")
    }

    override fun onCancel() {
        println("ActorModelProcessor: I've been cancelled... doing some clean up")
    }

    override fun onPause() {
        println("ActorModelProcessor: I've been paused....")
    }

    override fun onPlay() {
        println("ActorModelProcessor: I've playing again after having been paused...")
    }

    override fun onError(eventRecord: EventRecord<String>, e: Exception) {
        println("ActorModelProcessor: There was an error on ${eventRecord.eventSource} for key ${eventRecord.key}...")
    }
}

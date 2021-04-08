package api.samples.producerconsumer.processor.impl

import api.samples.producerconsumer.processor.ActorProcessor
import api.samples.producerconsumer.records.event.EventRecord
import api.samples.producerconsumer.records.state.StateRecord

class SimpleActorProcessor: ActorProcessor<String, String, String> {

    override fun onNext(state: StateRecord<String, String>,  event: EventRecord<String, String>
    ): Pair<StateRecord<String, String>, List<EventRecord<String, String>>> {
        println("SimpleActorProcessor: I'm processing my next record ${event.key} from eventTopic ${event.eventTopic} and " +
                "stateTopic ${state.stateTopic}")

        //logic to produce new state + events
        val newState = StateRecord("stateTopic", event.key, "some event value")
        val newEvents = mutableListOf(EventRecord("source", "key", "value"))

        return Pair(newState,newEvents)
    }

    override fun onError(state: StateRecord<String, String>, event: EventRecord<String, String>, e: Exception) {
        println("SimpleActorProcessor: There was an error on ${event.eventTopic} for key ${event.key}...")
    }


}

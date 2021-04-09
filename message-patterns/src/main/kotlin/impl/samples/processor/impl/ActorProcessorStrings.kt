package impl.samples.processor.impl

import api.samples.processor.ActorProcessor
import api.samples.records.Record

class ActorProcessorStrings: ActorProcessor<String, String, String> {

    override fun onNext(state: Record<String, String>, event: Record<String, String>): Pair<Record<String, String>, List<Record<*, *>>> {

        println("ActorProcessorStrings: I'm processing my next record ${event.key} from eventTopic ${event.topic} and " +
                "stateTopic ${state.topic}")

        //logic to produce new state + events
        val newState = Record("stateTopic", event.key, "some event value")
        val newEvents = mutableListOf(Record("source", "key", "value"))

        return Pair(newState,newEvents)
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val stateValueClass: Class<String>
        get() = String::class.java
    override val eventValueClass: Class<String>
        get() = String::class.java

}

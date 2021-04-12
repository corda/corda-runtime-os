package impl.samples.processor.impl

import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.records.StateAndEvent

class StateAndEventProcessorStrings:
    StateAndEventProcessor<String, String, String> {

    override fun onNext(stateAndEvent: StateAndEvent<String, String, String>): Pair<Record<String, String>, List<Record<*, *>>> {

        val state = stateAndEvent.state
        val event = stateAndEvent.event
        println("ActorProcessorStrings: I'm processing my next record ${event.key} from eventTopic ${event.topic} and " +
                "stateTopic ${state?.topic}")

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

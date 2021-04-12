package impl.samples.processor.impl

import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.records.Record

class PubSubProcessorStrings : PubSubProcessor<String, String> {
    override fun onNext(eventRecord: Record<String, String>) {
        println("PubSubProcessorStrings: I'm processing my next record ${eventRecord.key} from eventTopic ${eventRecord.topic}")
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<String>
        get() = String::class.java
}
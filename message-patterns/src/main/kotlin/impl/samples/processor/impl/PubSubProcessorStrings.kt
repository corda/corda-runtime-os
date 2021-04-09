package impl.samples.processor.impl

import api.samples.processor.PubSubProcessor
import api.samples.records.Record

class PubSubProcessorStrings : PubSubProcessor<String, String> {
    override fun onNext(eventRecord: Record<String, String>) {
        println("PubSubProcessorStrings: I'm processing my next record ${eventRecord.key} from eventTopic ${eventRecord.topic}")
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<String>
        get() = String::class.java
}
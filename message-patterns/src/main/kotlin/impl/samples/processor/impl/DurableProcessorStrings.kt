package impl.samples.processor.impl

import api.samples.processor.DurableProcessor
import api.samples.records.Record

class DurableProcessorStrings : DurableProcessor<String, String> {
    override fun onNext(event: Record<String, String>) : List<Record<String, String>> {
        println("DurableProcessorStrings: I'm processing my next record ${event.key} from eventTopic ${event.topic}")

        return mutableListOf()
    }


    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<String>
        get() = String::class.java
}
package impl.samples.processor.impl

import net.cordax.flowworker.api.processor.DurableProcessor
import net.cordax.flowworker.api.records.Record

class DurableProcessorStrings : DurableProcessor<String, String> {
    override fun onNext(event: net.cordax.flowworker.api.records.Record<String, String>) : List<net.cordax.flowworker.api.records.Record<*, *>> {
        println("DurableProcessorStrings: I'm processing my next record ${event.key} from eventTopic ${event.topic}")

        return mutableListOf()
    }


    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<String>
        get() = String::class.java
}
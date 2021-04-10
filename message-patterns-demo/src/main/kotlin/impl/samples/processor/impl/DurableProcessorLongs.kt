package impl.samples.processor.impl

import net.cordax.flowworker.api.processor.DurableProcessor
import net.cordax.flowworker.api.records.Record

class DurableProcessorLongs : net.cordax.flowworker.api.processor.DurableProcessor<Long, Long> {
    override fun onNext(event: net.cordax.flowworker.api.records.Record<Long, Long>) : List<net.cordax.flowworker.api.records.Record<Long, Long>> {
        println("DurableProcessorLongs: I'm processing my next record ${event.key} from eventTopic ${event.topic}")

        return mutableListOf()
    }


    override val keyClass: Class<Long>
        get() = Long::class.java
    override val valueClass: Class<Long>
        get() = Long::class.java
}
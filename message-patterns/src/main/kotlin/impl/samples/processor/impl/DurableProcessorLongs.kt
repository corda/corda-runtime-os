package impl.samples.processor.impl

import api.samples.processor.DurableProcessor
import api.samples.records.Record

class DurableProcessorLongs : DurableProcessor<Long, Long> {
    override fun onNext(event: Record<Long, Long>) : List<Record<Long, Long>> {
        println("DurableProcessorLongs: I'm processing my next record ${event.key} from eventTopic ${event.topic}")

        return mutableListOf()
    }


    override val keyClass: Class<Long>
        get() = Long::class.java
    override val valueClass: Class<Long>
        get() = Long::class.java
}
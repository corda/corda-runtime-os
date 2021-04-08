package api.samples.producerconsumer.processor.impl

import api.samples.producerconsumer.processor.Processor
import api.samples.producerconsumer.records.event.EventRecord

class SimpleProcessor : Processor<String, String> {
    override fun onNext(eventRecord: EventRecord<String, String>) : List<EventRecord<String, String>> {
        println("SimpleProcessor: I'm processing my next record ${eventRecord.key} from eventTopic ${eventRecord.eventTopic}")

        return mutableListOf()
    }

    override fun onError(eventRecord: EventRecord<String, String>, e: Exception) {
        println("SimpleProcessor: There was an error on ${eventRecord.eventTopic} for key ${eventRecord.key}...")
    }
}
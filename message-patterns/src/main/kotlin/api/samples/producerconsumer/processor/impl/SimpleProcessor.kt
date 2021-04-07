package api.samples.producerconsumer.processor.impl

import api.samples.producerconsumer.processor.Processor
import api.samples.producerconsumer.records.EventRecord

class SimpleProcessor : Processor<String> {
    override fun onNext(eventRecord: EventRecord<String>) {
        println("SimpleProcessor: I'm processing my next record ${eventRecord.key} from eventSource ${eventRecord.eventSource}")
    }

    override fun onSuccess(eventRecord: EventRecord<String>) {
        println("SimpleProcessor: Finished processing this ${eventRecord.key}")
    }

    override fun onCancel() {
        println("SimpleProcessor: I've been cancelled... doing some clean up")
    }

    override fun onPause() {
        println("SimpleProcessor: I've been paused....")
    }

    override fun onPlay() {
        println("SimpleProcessor: I've playing again after having been paused...")
    }

    override fun onError(eventRecord: EventRecord<String>, e: Exception) {
        println("SimpleProcessor: There was an error on ${eventRecord.eventSource} for key ${eventRecord.key}...")
    }
}
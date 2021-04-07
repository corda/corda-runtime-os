package api.samples.producerconsumer.processor

import api.samples.producerconsumer.records.EventRecord

interface Processor<T> {
    /**
     * process a record
     */
    fun onNext(eventRecord: EventRecord<T>)

    /**
     * After successful completion by processor, may be unnecessary
     */
    fun onSuccess(eventRecord: EventRecord<T>)

    /**
     * When subscription is cancelled,may be unnecessary
     */
    fun onCancel()

    /**
     * When subscription is paused, may be unnecessary
     */
    fun onPause()

    /**
     * When subscription is replayed after being paused, may be unnecessary
     */
    fun onPlay()

    /**
     * When error occurs processing a record. May be unnecessary
     */
    fun onError(eventRecord: EventRecord<T>, e: Exception)
}
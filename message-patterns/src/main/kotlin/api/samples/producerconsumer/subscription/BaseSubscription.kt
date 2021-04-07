package api.samples.producerconsumer.subscription

import api.samples.producerconsumer.processor.Processor


/**
 * Abstract class methods could be extended/or have concrete impls to handle some flows such as
 * - running consume loop and storing records in durable concurrent queue
 * - spawn threads that have processor read from durable queue to execute independent entries
 * - some back pressure logic when durable concurrent queue is full to pause consumption
 */
abstract class BaseSubscription (val eventSource: String, val processor: Processor) {
    abstract fun start()
    abstract fun pause()
    abstract fun play()
    abstract fun cancel()
}
package api.samples.producerconsumer.subscription

import api.samples.producerconsumer.processor.Processor
import api.samples.producerconsumer.records.EventRecord
import java.io.IOException
import kotlin.concurrent.thread
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.BlockingQueue

/**
 * simple impl to illustrate
 */
open class SimpleSubscription(eventSource: String, processor: Processor<String>) : BaseSubscription<String>(eventSource, processor) {
    @Volatile
    internal var cancelled = false
    @Volatile
    internal var running = true
    lateinit var consumeLoopThread: Thread
    lateinit var processLoopThread: Thread
    var blockingQueue: BlockingQueue<EventRecord<String>> = LinkedBlockingDeque()

    override fun start() {
        println("Running processor on $eventSource")

        consumeLoopThread =  thread(
            start = true,
            isDaemon = false,
            contextClassLoader = null,
            name = "consumer",
            priority = -1,
            ::runConsumeLoop
        )


        processLoopThread =  thread(
            start = true,
            isDaemon = false,
            contextClassLoader = null,
            name = "processor",
            priority = -1,
            ::runProcessLoop
        )
    }

    fun runConsumeLoop() {
        while (!cancelled) {
            if (running) {
                //logic to consume an event
                println("Subscription: Consuming entry from event source $eventSource")
                val eventRecord = getEventRecord(eventSource)

                //add some back pressure logic if queue is full

                blockingQueue.offer(eventRecord)
            }
        }
    }

    private fun getEventRecord(eventSource: String): EventRecord<String> {
        return EventRecord(eventSource, "key", "value")
    }

    open fun runProcessLoop() {
        while (!cancelled) {
            if (running) {
                process()
            }
        }
    }

    fun process() {
        //logic to get an event
        println("Subscription: Processing entry from queue for $eventSource")
        val record = blockingQueue.take()

        var errorOccurred = false
        try {
            //process it
            processor.onNext(record)

            //detekt complained about Exception being too generic
        } catch (e: IOException) {
            errorOccurred = true
            processor.onError(record, e)
            blockingQueue.offer(record)
        }

        if(!errorOccurred) {
            processor.onSuccess(record)
            //some logic to mark as consumed on the topic
        }
    }

    override fun pause() {
        println("Subscription: Pausing entry from topic $eventSource ....")
        running = false
        processor.onPause()
    }

    override fun cancel() {
        println("Subscription: cancelling subscription $eventSource ....")
        cancelled = true
        running = false
        processor.onCancel()
    }

    override fun play() {
        if (cancelled) {
            println("Can't play. This is cancelled. Create a new subscription")
        } else {
            running = true
            println("Subscription: Playing subscription $eventSource ....")
        }
    }
}
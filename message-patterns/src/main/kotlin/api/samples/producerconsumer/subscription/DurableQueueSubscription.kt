package api.samples.producerconsumer.subscription

import api.samples.producerconsumer.processor.Processor
import api.samples.producerconsumer.records.event.EventRecord
import java.io.IOException
import kotlin.concurrent.thread


open class DurableQueueSubscription(private val eventTopic: String, private val processor: Processor<String, String>,
                                    private val properties: Map<String, String>) : LifeCycle {
    @Volatile
    internal var cancelled = false
    @Volatile
    internal var running = true
    lateinit var consumeLoopThread: Thread

    override fun start() {
        println("Running processor on $eventTopic")

        consumeLoopThread =  thread(
            start = true,
            isDaemon = false,
            contextClassLoader = null,
            name = "consumer",
            priority = -1,
            ::runConsumeAndProcessLoop
        )
    }

    private fun runConsumeAndProcessLoop() {
        while (!cancelled) {
            if (running) {
                //logic to consume an event
                process()
            }
        }
    }

    private fun process() {
        //set up connection to sources

        //logic to get an event
        println("DurableQueueSubscription: Processing entry from queue for $eventTopic")
        val record = EventRecord(eventTopic, "key", "value")

        var errorOccurred = false
        try {
            //process it
            val recordsProduced  = processor.onNext(record)
        } catch (e: IOException) {
            errorOccurred = true
            processor.onError(record, e)
        }

        if (!errorOccurred) {
            //send off recordsProduced
            //some logic to set offsets to mark as consumed on the topic
        }
    }

    override fun cancel() {
        println("DurableQueueSubscription: cancelling subscription $eventTopic ....")
        cancelled = true
        running = false
    }


    override fun pause() {
        println("DurableQueueSubscription: Pausing entry from topic $eventTopic ....")
        running = false
    }

    override fun play() {
        if (cancelled) {
            println("Can't play. This is cancelled. Create a new subscription")
        } else {
            running = true
            println("DurableQueueSubscription: Playing subscription $eventTopic ....")
        }
    }
}
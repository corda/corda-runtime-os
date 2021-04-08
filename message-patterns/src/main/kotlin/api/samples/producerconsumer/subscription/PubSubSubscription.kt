package api.samples.producerconsumer.subscription

import api.samples.producerconsumer.processor.Processor
import api.samples.producerconsumer.records.EventRecord
import java.io.IOException
import kotlin.concurrent.thread
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ExecutorService

/**
 * simple impl to illustrate
 */
open class PubSubSubscription(private val eventTopic: String, private val processor: Processor<String, String>,
                              private val executorService: ExecutorService) : LifeCycle {
    @Volatile
    internal var cancelled = false
    @Volatile
    internal var running = true
    lateinit var consumeLoopThread: Thread
    lateinit var processLoopThread: Thread
    var blockingQueue: BlockingQueue<EventRecord<String, String>> = LinkedBlockingDeque()

    override fun start() {
        println("Running processor on $eventTopic")

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

    private fun runProcessLoop() {
        while (!cancelled) {
            if (running) {
                //Some logic to use executor to process from queue in multi-threaded fashion
                executorService.execute(::process)
            }
        }
    }

    private fun runConsumeLoop() {
        while (!cancelled) {
            if (running) {
                //set up connection to sources

                //logic to consume an event
                println("PubSubSubscription: Consuming entry from event source $eventTopic")
                val eventRecord = EventRecord(eventTopic, "key", "value")

                //could add some back pressure logic if queue is full
                blockingQueue.offer(eventRecord)
            }
        }
    }


    fun process() {

        //logic to get an event
        println("PubSubSubscription: Processing entry from queue for $eventTopic")
        val record = blockingQueue.take()

        var errorOccurred = false
        try {
            //process it
            val recordsProduced = processor.onNext(record)

        } catch (e: IOException) {
            errorOccurred = true
            processor.onError(record, e)
        }

        if (!errorOccurred) {
            //some logic to set offsets to mark as consumed on the topic
            //send off recordsProduced
        }
    }

    override fun cancel() {
        println("PubSubSubscription: cancelling subscription $eventTopic ....")
        cancelled = true
        running = false
    }

    override fun pause() {
        println("PubSubSubscription: Pausing entry from topic $eventTopic ....")
        running = false
    }

    override fun play() {
        if (cancelled) {
            println("Can't play. This is cancelled. Create a new subscription")
        } else {
            running = true
            println("PubSubSubscription: Playing subscription $eventTopic ....")
        }
    }
}
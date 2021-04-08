package api.samples.producerconsumer.subscription

import api.samples.producerconsumer.processor.ActorProcessor
import api.samples.producerconsumer.processor.Processor
import api.samples.producerconsumer.records.EventRecord
import api.samples.producerconsumer.records.StateRecord
import java.io.IOException
import kotlin.concurrent.thread


open class ActorSubscription(private val eventTopic: String, private val stateTopic: String, private val processor: ActorProcessor<String, String, String>) : LifeCycle {
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

    fun runConsumeAndProcessLoop() {
        while (!cancelled) {
            if (running) {
                //logic to consume an event
                process()
            }
        }
    }

    private fun process() {

        //set up connection to sources

        //logic to get an event/state
        println("ActorSubscription: Polling entry from queue for $eventTopic & $stateTopic)")
        val event = EventRecord(eventTopic, "key", "value")
        val state = StateRecord(stateTopic, "key", "value")

        var errorOccurred = false
        try {
            //process it
            val recordsProduced = processor.onNext(state, event)

        } catch (e: IOException) {
            errorOccurred = true
            processor.onError(state, event, e)
        }

        if (!errorOccurred) {
            //send off recordsProduced
            //some logic to set offsets to mark as consumed on the topic
        }
    }

    override fun cancel() {
        println("ActorSubscription: cancelling subscription $eventTopic ....")
        cancelled = true
        running = false
    }

    override fun pause() {
        println("ActorSubscription: Pausing entry from topic $eventTopic ....")
        running = false
    }

    override fun play() {
        if (cancelled) {
            println("Can't play. This is cancelled. Create a new subscription")
        } else {
            running = true
            println("ActorSubscription: Playing subscription $eventTopic ....")
        }
    }
}
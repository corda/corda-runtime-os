package impl.samples.subscription.subscriptions

import api.samples.processor.ActorProcessor
import api.samples.records.Record
import api.samples.subscription.LifeCycle
import api.samples.subscription.Subscription
import java.io.IOException
import java.lang.Exception
import kotlin.concurrent.thread

class ActorSubscription<K, S, E> (
    private val groupName: String,
    private val instanceId: Int,
    private val eventTopic: String,
    private val stateTopic: String,
    private val processor: ActorProcessor<K, S, E>,
    private val properties: Map<String, String>) : LifeCycle {

    @Volatile
    internal var stopped = false

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
        while (!stopped) {
            try {
                process()
            } catch (e: Exception) {
                //
            }
        }
    }

    private fun process() {
        //logic to get an event/state
        println("ActorSubscription: Polling entry from queue for $eventTopic & $stateTopic)")
        val state = getState()
        val event = getEvent()

        var recordsProduced : Pair<Record<K, S>, List<Record<*, *>>> = processor.onNext(state, event)

        //send off recordsProduced
        //some logic to set offsets to mark as consumed on the topic

    }

    private fun getState(): Record<K, S> {
        TODO("Not yet implemented")
    }

    private fun getEvent(): Record<K, E> {
        TODO("Not yet implemented")
    }

    override fun stop() {
        println("ActorSubscription: cancelling subscription $eventTopic ....")
        stopped = true
    }

}
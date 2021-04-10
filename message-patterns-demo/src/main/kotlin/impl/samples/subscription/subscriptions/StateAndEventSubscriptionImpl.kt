package impl.samples.subscription.subscriptions

import net.cordax.flowworker.api.processor.StateAndEventProcessor
import net.cordax.flowworker.api.records.Record
import net.cordax.flowworker.api.subscription.StateAndEventSubscription
import java.lang.Exception
import kotlin.concurrent.thread

class StateAndEventSubscriptionImpl<K, S, E> (
    private val groupName: String,
    private val instanceId: Int,
    private val eventTopic: String,
    private val stateTopic: String,
    private val processor: StateAndEventProcessor<K, S, E>,
    private val properties: Map<String, String>) : StateAndEventSubscription<K, S, E> {

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
        val state = getState(processor.keyClass, processor.stateValueClass)
        val event = getEvent(processor.keyClass, processor.eventValueClass)

        var recordsProduced : Pair<Record<K, S>, List<Record<*, *>>> = processor.onNext(state, event)

        //send off recordsProduced
        //some logic to set offsets to mark as consumed on the topic

    }

    private fun getState(keyClass: Class<K>, valueClass: Class<S> ): Record<K, S> {
        var key = keyClass.cast("EVENT_KEY1")
        var value = valueClass.cast("STATE_VALUE2")

        return Record("topic", key, value)
    }


    private fun getEvent(keyClass: Class<K>, valueClass: Class<E> ): Record<K, E> {
        var key = keyClass.cast("KEY1")
        var value = valueClass.cast("EVENT_VALUE2")
        return Record("topic", key, value)
    }

    override fun stop() {
        println("ActorSubscription: cancelling subscription $eventTopic ....")
        stopped = true
    }

}
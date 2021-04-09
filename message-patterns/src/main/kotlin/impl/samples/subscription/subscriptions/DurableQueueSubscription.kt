package impl.samples.subscription.subscriptions

import api.samples.processor.DurableProcessor
import api.samples.records.Record
import api.samples.subscription.LifeCycle
import api.samples.subscription.Subscription
import java.io.IOException
import java.lang.Exception
import kotlin.concurrent.thread

class DurableQueueSubscription<K,V> constructor(
    private val groupName: String,
    private val instanceId: Int,
    private val eventTopic: String,
    private val processor: DurableProcessor<K, V>,
    private val properties: Map<String, String>) : LifeCycle {

    @Volatile
    internal var cancelled = false
    @Volatile
    internal var running = true
    lateinit var consumeLoopThread: Thread

    override fun start() {

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
                try {
                    process()
                } catch (e: Exception) {
                    //
                }
            }
        }
    }

    private fun process() {
        //logic to get an event/state
        val state = getState()

        var recordsProduced : List<Record<*, *>> = processor.onNext(state)

        //send off recordsProduced
        //some logic to set offsets to mark as consumed on the topic

    }

    private fun getState(): Record<K, V> {
            TODO("Not yet implemented")
    }

    override fun stop() {
        cancelled = true
        running = false
    }

}
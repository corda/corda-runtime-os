package impl.samples.subscription.subscriptions

import net.cordax.flowworker.api.processor.DurableProcessor
import net.cordax.flowworker.api.records.Record
import net.cordax.flowworker.api.subscription.LifeCycle
import net.cordax.flowworker.api.subscription.Subscription
import java.io.File
import java.lang.Exception
import kotlin.concurrent.thread

class DurableQueueSubscriptionImpl<K,V> constructor(
    private val groupName: String,
    private val instanceId: Int,
    private val eventTopic: String,
    private val processor: DurableProcessor<K, V>,
    private val properties: Map<String, String>) : Subscription<K, V> {

    @Volatile
    internal var cancelled = false

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
            try {
                process()
            } catch (e: Exception) {
                //
            }
        }
    }

    private fun process() {
        //logic to get an event/state
        val state = getEvent(processor.keyClass, processor.valueClass)

        var recordsProduced : List<Record<*, *>> = processor.onNext(state)

        //send off recordsProduced
        //some logic to set offsets to mark as consumed on the topic

    }

    private fun getEvent(keyClass: Class<K>, valueClass: Class<V> ): Record<K, V> {
        var key = keyClass.cast("EVENT_KEY1")
        var value = valueClass.cast("EVENT_VALUE2")
        return Record("topic", key, value)
    }

    override fun stop() {
        cancelled = true
    }

}
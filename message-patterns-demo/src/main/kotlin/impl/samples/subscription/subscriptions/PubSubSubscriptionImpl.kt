package impl.samples.subscription.subscriptions

import net.cordax.flowworker.api.processor.PubSubProcessor
import net.cordax.flowworker.api.records.Record
import net.cordax.flowworker.api.subscription.LifeCycle
import net.cordax.flowworker.api.subscription.Subscription
import java.io.IOException
import kotlin.concurrent.thread
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ExecutorService

/**
 * simple impl to illustrate
 */
class PubSubSubscriptionImpl<K, V> constructor(
    private val groupName: String,
    private val instanceId: Int,
    private val eventTopic: String,
    private val processor: PubSubProcessor<K, V>,
    private val executor: ExecutorService,
    private val properties: Map<String, String>) : Subscription<K, V> {

    @Volatile
    internal var cancelled = false

    lateinit var consumeLoopThread: Thread
    lateinit var processLoopThread: Thread
    var blockingQueue: BlockingQueue<Record<K, V>> = LinkedBlockingDeque()

    override fun start() {

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
            //Some logic to use executor to process from queue in multi-threaded fashion
            executor.execute(::process)
        }
    }

    private fun runConsumeLoop() {
        while (!cancelled) {
            //set up connection to sources

            //logic to consume an event
            val eventRecord = getEvent(processor.keyClass, processor.valueClass)

            //could add some back pressure logic if queue is full
            blockingQueue.offer(eventRecord)
        }
    }

    private fun getEvent(keyClass: Class<K>, valueClass: Class<V> ): Record<K, V> {
        var key = keyClass.cast("EVENT_KEY1")
        var value = valueClass.cast("EVENT_VALUE2")
        return Record("topic", key, value)
    }


    fun process() {
        //logic to get an event
        val record = blockingQueue.take()


        processor.onNext(record)

    }

    override fun stop() {
        cancelled = true
        executor.shutdown()
    }

}
package impl.samples.subscription.subscriptions

import api.samples.processor.PubSubProcessor
import api.samples.records.Record
import api.samples.subscription.LifeCycle
import api.samples.subscription.Subscription
import java.io.IOException
import kotlin.concurrent.thread
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ExecutorService

/**
 * simple impl to illustrate
 */
class PubSubSubscription<K, V> constructor(
    private val groupName: String,
    private val instanceId: Int,
    private val eventTopic: String,
    private val processor: PubSubProcessor<K, V>,
    private val executor: ExecutorService,
    private val properties: Map<String, String>) : LifeCycle {

    @Volatile
    internal var cancelled = false
    @Volatile
    internal var running = true
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
            if (running) {
                //Some logic to use executor to process from queue in multi-threaded fashion
                executor.execute(::process)
            }
        }
    }

    private fun runConsumeLoop() {
        while (!cancelled) {
            if (running) {
                //set up connection to sources

                //logic to consume an event
                val eventRecord = getEvent(processor.keyClass, processor.valueClass)

                //could add some back pressure logic if queue is full
                blockingQueue.offer(eventRecord)
            }
        }
    }

    private fun getEvent(keyClazz: Class<K>, value: Class<V> ): Record<K, V> {
        var key = keyClazz.newInstance()
        var value = value.newInstance()
        return Record("topic", key, value)
    }


    fun process() {
        //logic to get an event
        val record = blockingQueue.take()


        processor.onNext(record)

    }

    override fun stop() {
        cancelled = true
        running = false
        executor.shutdown()
    }

}
package net.corda.messaging.kafka.publisher

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.kafka.producer.wrapper.CordaKafkaProducer
import net.corda.messaging.kafka.properties.KafkaProperties
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.utils.render
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.slf4j.Logger
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

class CordaKafkaRPCSenderImpl<TREQ : Any, TRESP : Any>(
    private val config: Config,
    private val cordaKafkaProducer: CordaKafkaProducer,
    private val cordaKafkaConsumer: CordaKafkaConsumer<TREQ, TRESP>
) : RPCSender<TREQ, TRESP> {

    private companion object {
        private val log: Logger = contextLogger()
    }

    private val consumerThreadStopTimeout = config.getLong(KafkaProperties.CONSUMER_THREAD_STOP_TIMEOUT)
    private val topicSuffix = config.getString(KafkaProperties.TOPIC_SUFFIX)
    private val groupName = config.getString(KafkaProperties.CONSUMER_GROUP_ID)
    private val topic = config.getString(KafkaProperties.TOPIC_NAME)


    @Volatile
    private var stopped = false
    private val lock = ReentrantLock()
    private var consumeLoopThread: Thread? = null

    override val isRunning: Boolean
        get() = !stopped

    override fun start() {
        log.debug { "Starting subscription with config:\n${config.render()}" }
        lock.withLock {
            if (consumeLoopThread == null) {
                stopped = false
                consumeLoopThread = thread(
                    start = true,
                    isDaemon = true,
                    contextClassLoader = null,
                    name = "compacted subscription thread $groupName-$topic",
                    priority = -1,
                    block = ::runConsumeLoop
                )
            }
        }
    }

    override fun stop() {
        if (!stopped) {
            val thread = lock.withLock {
                stopped = true
                val threadTmp = consumeLoopThread
                consumeLoopThread = null
                threadTmp
            }
            thread?.join(consumerThreadStopTimeout)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun runConsumeLoop() {
        var attempts = 0
        while (!stopped) {
            attempts++
            try {
                log.debug { "Creating compacted consumer.  Attempt: $attempts" }
                consumerBuilder.createCompactedConsumer(config.getConfig(KafkaProperties.KAFKA_CONSUMER), processor.keyClass, processor.valueClass).use {
                    val partitions = it.getPartitions(
                        topicPrefix + topic,
                        Duration.ofSeconds(consumerThreadStopTimeout)
                    )
                    it.assign(partitions)
                    pollAndProcessSnapshot(it)
                    pollAndProcessRecords(it)
                }
                attempts = 0
            } catch (ex: Exception) {
                when (ex) {
                    is CordaMessageAPIIntermittentException -> {
                        log.warn("$errorMsg. Attempts: $attempts. Retrying.", ex)
                    }
                    else -> {
                        log.error("$errorMsg. Fatal error occurred. Closing subscription.", ex)
                        stop()
                    }
                }
            }
        }
    }

    override fun sendRequest(req: TREQ): CompletableFuture<TRESP> {
        TODO("Not yet implemented")
    }

}
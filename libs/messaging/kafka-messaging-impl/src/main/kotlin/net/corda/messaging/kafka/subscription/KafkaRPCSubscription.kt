package net.corda.messaging.kafka.subscription

import com.typesafe.config.Config
import net.corda.data.messaging.RPCRequest
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.messaging.kafka.properties.KafkaProperties
import net.corda.messaging.kafka.subscription.consumer.builder.impl.CordaKafkaConsumerBuilderImpl
import net.corda.messaging.kafka.subscription.consumer.wrapper.ConsumerRecordAndMeta
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.utils.render
import net.corda.v5.base.util.debug
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ObjectInput
import java.io.ObjectInputStream
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

class KafkaRPCSubscription<TREQ : Any, TRESP : Any>(
    private val config: Config,
    private val consumerBuilder: CordaKafkaConsumerBuilderImpl<String, RPCRequest>,
    private val responderProcessor: RPCResponderProcessor<TREQ, TRESP>
) : RPCSubscription<TREQ, TRESP> {

    private val log = LoggerFactory.getLogger(
        config.getString(KafkaProperties.CONSUMER_GROUP_ID)
    )

    private val consumerThreadStopTimeout = config.getLong(KafkaProperties.CONSUMER_THREAD_STOP_TIMEOUT)
    private val topicPrefix = config.getString(KafkaProperties.TOPIC_PREFIX)
    private val groupName = config.getString(KafkaProperties.CONSUMER_GROUP_ID)
    private val topic = config.getString(KafkaProperties.TOPIC_NAME)

    private val errorMsg = "Failed to read records from group $groupName, topic $topic"

    @Volatile
    private var stopped = false
    private val lock = ReentrantLock()
    private var consumeLoopThread: Thread? = null

    var partitions: List<TopicPartition> = listOf()

    override val isRunning: Boolean
        get() = stopped

    override fun start() {
        log.debug { "Starting subscription with config:\n${config.render()}" }
        lock.withLock {
            if (consumeLoopThread == null) {
                stopped = false
                consumeLoopThread = thread(
                    start = true,
                    isDaemon = true,
                    contextClassLoader = null,
                    name = "rpc subscription thread $groupName-$topic",
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
                log.debug { "Creating rpc consumer.  Attempt: $attempts" }
                consumerBuilder.createRPCConsumer(
                    config.getConfig(KafkaProperties.KAFKA_CONSUMER),
                    String::class.java,
                    RPCRequest::class.java
                ).use {
                    partitions = it.getPartitions(
                        topicPrefix + topic,
                        Duration.ofSeconds(consumerThreadStopTimeout)
                    )
                    it.assign(partitions)
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

    @Suppress("TooGenericExceptionCaught")
    private fun pollAndProcessRecords(consumer: CordaKafkaConsumer<String, RPCRequest>) {
        while (!stopped) {
            val consumerRecords = consumer.poll()
            try {
                processRecords(consumerRecords)
            } catch (ex: Exception) {
                when (ex) {
                    is CordaMessageAPIFatalException,
                    is CordaMessageAPIIntermittentException -> {
                        throw ex
                    }
                    else -> {
                        throw CordaMessageAPIFatalException(
                            "Failed to process records from topic $topic, group $groupName.", ex
                        )
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun processRecords(consumerRecords: List<ConsumerRecordAndMeta<String, RPCRequest>>) {
        consumerRecords.forEach {
            val requestBytes = it.record.value().payload
            val byteArrayInputStream = ByteArrayInputStream(requestBytes.array())
            val objectInput: ObjectInput
            objectInput = ObjectInputStream(byteArrayInputStream)
            val request = objectInput.readObject() as TREQ
            byteArrayInputStream.close()

            responderProcessor.onNext(request, CompletableFuture<TRESP>())
        }
    }
}

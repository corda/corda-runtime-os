package net.corda.messaging.kafka.subscription

import com.typesafe.config.Config
import net.corda.data.messaging.RPCRequest
import net.corda.data.messaging.RPCResponse
import net.corda.data.messaging.ResponseStatus
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.kafka.properties.KafkaProperties
import net.corda.messaging.kafka.publisher.CordaAvroSerializer
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.ConsumerRecordAndMeta
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.utils.render
import net.corda.v5.base.util.debug
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

@Suppress("LongParameterList")
class KafkaRPCSubscriptionImpl<TREQ : Any, TRESP : Any>(
    private val config: Config,
    private val publisher: Publisher,
    private val consumerBuilder: ConsumerBuilder<String, RPCRequest>,
    private val responderProcessor: RPCResponderProcessor<TREQ, TRESP>,
    private val serializer: CordaAvroSerializer<TRESP>,
    private val deserializer: CordaAvroDeserializer<TREQ>
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
                    it.subscribe(
                        listOf("$topicPrefix$topic")
                    )
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

    @Suppress("TooGenericExceptionCaught")
    private fun processRecords(consumerRecords: List<ConsumerRecordAndMeta<String, RPCRequest>>) {
        consumerRecords.forEach {
            val rpcRequest = it.record.value()
            val requestBytes = rpcRequest.payload
            val request = deserializer.deserialize(topic, requestBytes.array())
            val future = CompletableFuture<TRESP>()

            future.whenComplete { response, error ->
                val record: Record<String, RPCResponse>?
                when {
                    //TODOs: convert error string to actual error object
                    //the order of these is important due to how the futures api is
                    future.isCancelled -> {
                        record = buildRecord(
                            rpcRequest.replyTopic,
                            rpcRequest.correlationKey,
                            ResponseStatus.CANCELLED,
                            error.message.toString().encodeToByteArray()
                        )
                    }
                    future.isCompletedExceptionally -> {
                        record = buildRecord(
                            rpcRequest.replyTopic,
                            rpcRequest.correlationKey,
                            ResponseStatus.FAILED,
                            error.message.toString().encodeToByteArray()
                        )
                    }
                    else -> {
                        val serializedResponse = serializer.serialize(rpcRequest.replyTopic, response)
                        record = buildRecord(
                            rpcRequest.replyTopic,
                            rpcRequest.correlationKey,
                            ResponseStatus.OK,
                            serializedResponse!!
                        )
                    }
                }

                try {
                    publisher.publishToPartition(listOf(Pair(rpcRequest.replyPartition, record)))
                } catch (ex: Exception) {
                    //intentionally swallowed
                    log.warn("Error publishing response", ex)
                }

            }
            responderProcessor.onNext(request!!, future)
        }
    }

    private fun buildRecord(
        topic: String,
        key: String,
        status: ResponseStatus,
        payload: ByteArray
    ): Record<String, RPCResponse> {
        return Record(
            topic,
            key,
            RPCResponse(
                key,
                Instant.now().toEpochMilli(),
                status,
                ByteBuffer.wrap(payload)
            )
        )
    }
}

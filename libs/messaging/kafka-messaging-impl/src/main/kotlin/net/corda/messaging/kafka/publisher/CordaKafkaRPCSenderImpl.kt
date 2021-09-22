package net.corda.messaging.kafka.publisher

import com.typesafe.config.Config
import net.corda.data.messaging.RPCRequest
import net.corda.data.messaging.RPCResponse
import net.corda.data.messaging.ResponseStatus
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.CONSUMER_GROUP_ID
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.CONSUMER_THREAD_STOP_TIMEOUT
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.KAFKA_CONSUMER
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.RESPONSE_TOPIC
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.TOPIC_NAME
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.TOPIC_PREFIX
import net.corda.messaging.kafka.subscription.CordaAvroDeserializer
import net.corda.messaging.kafka.subscription.consumer.builder.impl.CordaKafkaConsumerBuilderImpl
import net.corda.messaging.kafka.subscription.consumer.listener.RPCConsumerRebalanceListener
import net.corda.messaging.kafka.subscription.consumer.wrapper.ConsumerRecordAndMeta
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.utils.render
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.infinispan.commons.util.WeakValueHashMap
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger
import java.nio.ByteBuffer
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

@Suppress("LongParameterList")
@Component
class CordaKafkaRPCSenderImpl<TREQ : Any, TRESP : Any>(
    private val config: Config,
    private val publisher: Publisher,
    private val consumerBuilder: CordaKafkaConsumerBuilderImpl<String, RPCResponse>,
    private val serializer: CordaAvroSerializer<TREQ>,
    private val deserializer: CordaAvroDeserializer<TRESP>,
    private val errorDeserializer: CordaAvroDeserializer<String>
) : RPCSender<TREQ, TRESP>, RPCSubscription<TREQ, TRESP> {

    private companion object {
        private val log: Logger = contextLogger()
    }

    @Volatile
    private var stopped = false
    private val lock = ReentrantLock()
    private var consumeLoopThread: Thread? = null
    private val futureMap: WeakValueHashMap<String, CompletableFuture<TRESP>> = WeakValueHashMap()

    override val isRunning: Boolean
        get() = !stopped

    private val consumerThreadStopTimeout = config.getLong(CONSUMER_THREAD_STOP_TIMEOUT)
    private val topicPrefix = config.getString(TOPIC_PREFIX)
    private val groupName = config.getString(CONSUMER_GROUP_ID)
    private val topic = config.getString(TOPIC_NAME)
    private val responseTopic = config.getString(RESPONSE_TOPIC)
    private var partitionListener = RPCConsumerRebalanceListener("$topicPrefix$responseTopic", "RPC Response listener")

    private val errorMsg = "Failed to read records from group $groupName, topic $topic"

    override fun start() {
        log.debug { "Starting subscription with config:\n${config.render()}" }
        lock.withLock {
            if (consumeLoopThread == null) {
                stopped = false
                consumeLoopThread = thread(
                    start = true,
                    isDaemon = true,
                    contextClassLoader = null,
                    name = "rpc response subscription thread $groupName-$topic",
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
                log.debug { "Creating rpc response consumer.  Attempt: $attempts" }
                consumerBuilder.createRPCConsumer(
                    config.getConfig(KAFKA_CONSUMER),
                    String::class.java,
                    RPCResponse::class.java
                ).use {
                    it.subscribe(
                        listOf("$topicPrefix$responseTopic"),
                        partitionListener
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
    private fun pollAndProcessRecords(consumer: CordaKafkaConsumer<String, RPCResponse>) {
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

    private fun processRecords(consumerRecords: List<ConsumerRecordAndMeta<String, RPCResponse>>) {
        consumerRecords.forEach {
            val correlationKey = it.record.key()
            val future = futureMap[correlationKey]

            if (future != null) {
                when (it.record.value().responseStatus!!) {
                    ResponseStatus.OK -> {
                        val responseBytes = it.record.value().payload
                        val response = deserializer.deserialize("$responseTopic", responseBytes.array())
                        log.info("Response for request $correlationKey was received at ${Date(it.record.value().sendTime)}")

                        future.complete(response)
                    }
                    ResponseStatus.FAILED -> {
                        val responseBytes = it.record.value().payload
                        val response = errorDeserializer.deserialize("$responseTopic", responseBytes.array())
                        future.completeExceptionally(CordaMessageAPIFatalException(response))
                    }
                    ResponseStatus.CANCELLED -> {
                        future.cancel(true)
                    }

                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun sendRequest(req: TREQ): CompletableFuture<TRESP> {
        val uuid = UUID.randomUUID().toString()
        val reqBytes = serializer.serialize(topic, req)
        val future = CompletableFuture<TRESP>()

        if (partitionListener.partitions.size == 0) {
            future.completeExceptionally(CordaMessageAPIFatalException("No partitions. Couldn't send"))
        } else {
            val request = RPCRequest(
                uuid,
                Instant.now().toEpochMilli(),
                "$topicPrefix$responseTopic",
                partitionListener.partitions[0].partition(),
                ByteBuffer.wrap(reqBytes)
            )

            val record = Record(topic, uuid, request)
            futureMap[uuid] = future
            try {
                publisher.publish(listOf(record))
            } catch (ex: Exception) {
                future.completeExceptionally(CordaMessageAPIFatalException("Failed to publish", ex))
            }
        }

        return future
    }
}
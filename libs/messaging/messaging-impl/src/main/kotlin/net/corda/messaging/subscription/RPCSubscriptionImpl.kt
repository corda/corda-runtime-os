package net.corda.messaging.subscription

import com.typesafe.config.Config
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializer
import net.corda.data.ExceptionEnvelope
import net.corda.data.messaging.RPCRequest
import net.corda.data.messaging.RPCResponse
import net.corda.data.messaging.ResponseStatus
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.CONSUMER_GROUP_ID
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.CONSUMER_THREAD_STOP_TIMEOUT
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.kafka.properties.ConfigProperties
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.KAFKA_CONSUMER
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.TOPIC_NAME
import net.corda.messaging.kafka.utils.render
import net.corda.messaging.subscription.consumer.builder.CordaConsumerBuilder
import net.corda.v5.base.util.debug
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

@Suppress("LongParameterList")
class RPCSubscriptionImpl<REQUEST : Any, RESPONSE : Any>(
    private val config: Config,
    private val publisher: Publisher,
    private val cordaConsumerBuilder: CordaConsumerBuilder,
    private val responderProcessor: RPCResponderProcessor<REQUEST, RESPONSE>,
    private val serializer: CordaAvroSerializer<RESPONSE>,
    private val deserializer: CordaAvroDeserializer<REQUEST>,
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
) : RPCSubscription<REQUEST, RESPONSE> {

    private val log = LoggerFactory.getLogger(
        config.getString(CONSUMER_GROUP_ID)
    )

    private val consumerThreadStopTimeout = config.getLong(CONSUMER_THREAD_STOP_TIMEOUT)
    private val groupName = config.getString(CONSUMER_GROUP_ID)
    private val topic = config.getString(TOPIC_NAME)
    private val lifecycleCoordinator = lifecycleCoordinatorFactory.createCoordinator(
        LifecycleCoordinatorName(
            "$groupName-KafkaRPCSubscription-$topic",
            //we use instanceId here as transactionality is a concern in this subscription
            config.getString(ConfigProperties.INSTANCE_ID)
        )
    ) { _, _ -> }

    private val errorMsg = "Failed to read records from group $groupName, topic $topic"

    @Volatile
    private var stopped = false
    private val lock = ReentrantLock()
    private var consumeLoopThread: Thread? = null

    override val isRunning: Boolean
        get() = !stopped

    override val subscriptionName: LifecycleCoordinatorName
        get() = lifecycleCoordinator.name

    override fun start() {
        log.debug { "Starting subscription with config:\n${config.render()}" }
        lock.withLock {
            if (consumeLoopThread == null) {
                stopped = false
                lifecycleCoordinator.start()
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
            stopConsumeLoop()
            lifecycleCoordinator.stop()
        }
    }

    override fun close() {
        if (!stopped) {
            stopConsumeLoop()
            lifecycleCoordinator.close()
        }
    }

    private fun stopConsumeLoop() {
        val thread = lock.withLock {
            stopped = true
            val threadTmp = consumeLoopThread
            consumeLoopThread = null
            threadTmp
        }
        thread?.join(consumerThreadStopTimeout)
    }

    private fun runConsumeLoop() {
        var attempts = 0
        while (!stopped) {
            attempts++
            try {
                log.debug { "Creating rpc consumer.  Attempt: $attempts" }
                cordaConsumerBuilder.createRPCConsumer(
                    config.getConfig(KAFKA_CONSUMER),
                    String::class.java,
                    RPCRequest::class.java
                ).use {
                    it.subscribe(topic)
                    lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
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
                        lifecycleCoordinator.updateStatus(LifecycleStatus.ERROR, errorMsg)
                        stop()
                    }
                }
            }
        }
        lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
    }

    private fun pollAndProcessRecords(consumer: CordaConsumer<String, RPCRequest>) {
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
    private fun processRecords(cordaConsumerRecords: List<CordaConsumerRecord<String, RPCRequest>>) {
        cordaConsumerRecords.forEach {
            val rpcRequest = it.value ?: throw CordaMessageAPIIntermittentException("Should we not have a request?")
            val requestBytes = rpcRequest.payload
            val request = deserializer.deserialize(requestBytes.array())
            val future = CompletableFuture<RESPONSE>()

            future.whenComplete { response, error ->
                val record: Record<String, RPCResponse>?
                when {
                    //the order of these is important due to how the futures api is
                    future.isCancelled -> {
                        record = buildRecord(
                            rpcRequest.replyTopic,
                            rpcRequest.correlationKey,
                            ResponseStatus.CANCELLED,
                            ExceptionEnvelope(
                                error.javaClass.name,
                                "Future was cancelled"
                            ).toByteBuffer().array()
                        )
                    }
                    future.isCompletedExceptionally -> {
                        record = buildRecord(
                            rpcRequest.replyTopic,
                            rpcRequest.correlationKey,
                            ResponseStatus.FAILED,
                            ExceptionEnvelope(error.javaClass.name, error.message).toByteBuffer().array()
                        )
                    }
                    else -> {
                        val serializedResponse = serializer.serialize(response)
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

package net.corda.messaging.subscription

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializer
import net.corda.data.ExceptionEnvelope
import net.corda.data.messaging.RPCRequest
import net.corda.data.messaging.RPCResponse
import net.corda.data.messaging.ResponseStatus
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messagebus.api.configuration.ConsumerConfig
import net.corda.messagebus.api.configuration.ProducerConfig
import net.corda.messagebus.api.constants.ConsumerRoles
import net.corda.messagebus.api.constants.ProducerRoles
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.builder.MessageBusConsumerBuilder
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.config.ResolvedSubscriptionConfig
import net.corda.v5.base.util.debug
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

@Suppress("LongParameterList")
internal class RPCSubscriptionImpl<REQUEST : Any, RESPONSE : Any>(
    private val config: ResolvedSubscriptionConfig,
    private val cordaConsumerBuilder: MessageBusConsumerBuilder,
    private val producerBuilder: CordaProducerBuilder,
    private val responderProcessor: RPCResponderProcessor<REQUEST, RESPONSE>,
    private val serializer: CordaAvroSerializer<RESPONSE>,
    private val deserializer: CordaAvroDeserializer<REQUEST>,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
) : RPCSubscription<REQUEST, RESPONSE> {

    private val log = LoggerFactory.getLogger(config.loggerName)

    private val lifecycleCoordinator = lifecycleCoordinatorFactory.createCoordinator(
        LifecycleCoordinatorName(
            "${config.group}-RPCSubscription-${config.topic}",
            //we use instanceId here as transactionality is a concern in this subscription
            config.instanceId.toString()
        )
    ) { _, _ -> }

    private val errorMsg = "Failed to read records from group ${config.group}, topic ${config.topic}"

    @Volatile
    private var stopped = false
    private val lock = ReentrantLock()
    private var consumeLoopThread: Thread? = null

    override val isRunning: Boolean
        get() = !stopped

    override val subscriptionName: LifecycleCoordinatorName
        get() = lifecycleCoordinator.name

    override fun start() {
        log.debug { "Starting subscription with config:\n$config" }
        lock.withLock {
            if (consumeLoopThread == null) {
                stopped = false
                lifecycleCoordinator.start()
                consumeLoopThread = thread(
                    start = true,
                    isDaemon = true,
                    contextClassLoader = null,
                    name = "rpc subscription thread ${config.group}-${config.topic}",
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
        thread?.join(config.threadStopTimeout.toMillis())
    }

    private fun runConsumeLoop() {
        var attempts = 0
        while (!stopped) {
            attempts++
            try {
                log.debug { "Creating rpc consumer.  Attempt: $attempts" }
                createProducerConsumerAndStartPolling()
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

    private fun createProducerConsumerAndStartPolling() {
        val producerConfig = ProducerConfig(config.clientId, config.instanceId, ProducerRoles.RPC_RESPONDER)
        producerBuilder.createProducer(producerConfig, config.busConfig).use { producer ->
            val consumerConfig = ConsumerConfig(config.group, config.clientId, ConsumerRoles.RPC_RESPONDER)
            cordaConsumerBuilder.createConsumer(
                consumerConfig,
                config.busConfig,
                String::class.java,
                RPCRequest::class.java
            ).use {
                it.subscribe(config.topic)
                lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
                pollAndProcessRecords(it, producer)
            }
        }
    }

    private fun pollAndProcessRecords(consumer: CordaConsumer<String, RPCRequest>, producer: CordaProducer) {
        while (!stopped) {
            val consumerRecords = consumer.poll(config.pollTimeout)
            try {
                processRecords(consumerRecords, producer)
            } catch (ex: Exception) {
                when (ex) {
                    is CordaMessageAPIFatalException,
                    is CordaMessageAPIIntermittentException -> {
                        throw ex
                    }
                    else -> {
                        throw CordaMessageAPIFatalException(
                            "Failed to process records from topic ${config.topic}, group ${config.group}.", ex
                        )
                    }
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun processRecords(
        consumerRecords: List<CordaConsumerRecord<String, RPCRequest>>,
        producer: CordaProducer
    ) {
        consumerRecords.forEach {
            val rpcRequest = it.value ?: throw CordaMessageAPIIntermittentException("Should we not have a request?")
            val requestBytes = rpcRequest.payload
            val request = deserializer.deserialize(requestBytes.array())
            val future = CompletableFuture<RESPONSE>()

            future.whenComplete { response, error ->
                val record: CordaProducerRecord<String, RPCResponse>?
                try {
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
                    producer.sendRecordsToPartitions(listOf(Pair(rpcRequest.replyPartition, record)))
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
    ): CordaProducerRecord<String, RPCResponse> {
        return CordaProducerRecord(
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

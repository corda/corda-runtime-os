package net.corda.messaging.publisher

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
import net.corda.messagebus.api.consumer.builder.CordaConsumerBuilder
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.exception.CordaRPCAPIResponderException
import net.corda.messaging.api.exception.CordaRPCAPISenderException
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.config.ResolvedSubscriptionConfig
import net.corda.messaging.subscription.consumer.listener.RPCConsumerRebalanceListener
import net.corda.messaging.utils.FutureTracker
import net.corda.schema.Schemas.Companion.getRPCResponseTopic
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.slf4j.Logger
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

@Suppress("LongParameterList")
internal class CordaRPCSenderImpl<REQUEST : Any, RESPONSE : Any>(
    private val config: ResolvedSubscriptionConfig,
    private val cordaConsumerBuilder: CordaConsumerBuilder,
    private val cordaProducerBuilder: CordaProducerBuilder,
    private val serializer: CordaAvroSerializer<REQUEST>,
    private val deserializer: CordaAvroDeserializer<RESPONSE>,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
) : RPCSender<REQUEST, RESPONSE>, RPCSubscription<REQUEST, RESPONSE> {

    private companion object {
        private val log: Logger = contextLogger()
    }

    @Volatile
    private var stopped = false
    private val lock = ReentrantLock()
    private var consumeLoopThread: Thread? = null


    override val isRunning: Boolean
        get() = !stopped

    override val subscriptionName: LifecycleCoordinatorName
        get() = lifecycleCoordinator.name

    private val futureTracker = FutureTracker<RESPONSE>()
    private var producer: CordaProducer? = null
    private val lifecycleCoordinator = lifecycleCoordinatorFactory.createCoordinator(config.lifecycleCoordinatorName) { _, _ -> }

    private val partitionListener = RPCConsumerRebalanceListener(
        getRPCResponseTopic(config.topic),
        "RPC Response listener",
        futureTracker,
        lifecycleCoordinator
    )

    private val errorMsg = "Failed to read records from group ${config.group}, topic ${config.topic}"

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
                    name = "rpc response subscription thread ${config.group}-${config.topic}",
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
            producer?.close()
            producer = null
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
                log.debug { "Creating rpc response consumer.  Attempt: $attempts" }
                val producerConfig = ProducerConfig(config.clientId, config.instanceId,false, ProducerRoles.RPC_SENDER)
                producer = cordaProducerBuilder.createProducer(producerConfig, config.messageBusConfig)
                val consumerConfig = ConsumerConfig(config.group, config.clientId, ConsumerRoles.RPC_SENDER)
                cordaConsumerBuilder.createConsumer(
                    consumerConfig,
                    config.messageBusConfig,
                    String::class.java,
                    RPCResponse::class.java
                ).use {
                    it.subscribe(
                        listOf(getRPCResponseTopic(config.topic)),
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
                        lifecycleCoordinator.updateStatus(LifecycleStatus.ERROR, errorMsg)
                        stop()
                    }
                }
            }
        }
        lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
    }

    private fun pollAndProcessRecords(consumer: CordaConsumer<String, RPCResponse>) {
        while (!stopped) {
            val consumerRecords = consumer.poll(config.pollTimeout)
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
                            "Failed to process records from topic ${getRPCResponseTopic(config.topic)}, group ${config.group}.",
                            ex
                        )
                    }
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun processRecords(cordaConsumerRecords: List<CordaConsumerRecord<String, RPCResponse>>) {
        cordaConsumerRecords.forEach {
            val correlationKey = it.key
            val partition = it.partition
            val future = futureTracker.getFuture(correlationKey, partition)
            val rpcResponse = it.value ?: throw CordaMessageAPIFatalException("Is this bad here?")

            val responseStatus = rpcResponse.responseStatus
                ?: throw CordaMessageAPIFatalException("Response status came back NULL. This should never happen")

            if (future != null) {
                when (responseStatus) {
                    ResponseStatus.OK -> {
                        val responseBytes = rpcResponse.payload
                        val response = deserializer.deserialize(responseBytes.array())
                        log.info("Response for request $correlationKey was received at ${rpcResponse.sendTime}")

                        future.complete(response)
                    }
                    ResponseStatus.FAILED -> {
                        val responseBytes = rpcResponse.payload
                        val response = ExceptionEnvelope.fromByteBuffer(responseBytes)
                        future.completeExceptionally(
                            CordaRPCAPIResponderException(
                                "Cause:${response.errorType}. Message: ${response.errorMessage}"
                            )
                        )
                        log.warn("Cause:${response.errorType}. Message: ${response.errorMessage}")
                    }
                    ResponseStatus.CANCELLED -> {
                        future.cancel(true)
                    }
                }
                futureTracker.removeFuture(correlationKey, partition)
            } else {
                log.info(
                    "Response for request $correlationKey was received at ${rpcResponse.sendTime}. " +
                            "There is no future assigned for $correlationKey meaning that this request was either orphaned during " +
                            "a repartition event or the client dropped their future. The response status for it was $responseStatus"
                )
            }
        }
    }

    override fun sendRequest(req: REQUEST): CompletableFuture<RESPONSE> {
        val correlationId = UUID.randomUUID().toString()
        val future = CompletableFuture<RESPONSE>()
        val partitions = partitionListener.getPartitions()
        var reqBytes: ByteArray? = null
        try {
            reqBytes = serializer.serialize(req)
        } catch (ex: Exception) {
            future.completeExceptionally(
                CordaRPCAPISenderException(
                    "Serializing your request resulted in an exception. " +
                            "Verify that the fields of the request are populated correctly", ex
                )
            )
            log.error(
                "Serializing your request resulted in an exception. " +
                        "Verify that the fields of the request are populated correctly. " +
                        "Request was: $req", ex
            )
        }

        if (partitions.isEmpty()) {
            future.completeExceptionally(CordaRPCAPISenderException("No partitions. Couldn't send"))
            log.error("No partitions. Couldn't send")
        } else {
            val partition = partitions[0].partition
            val request = RPCRequest(
                correlationId,
                Instant.now(),
                getRPCResponseTopic(config.topic),
                partition,
                ByteBuffer.wrap(reqBytes)
            )

            val record = CordaProducerRecord(config.topic, correlationId, request)
            futureTracker.addFuture(correlationId, future, partition)
            try {
                producer?.sendRecords(listOf(record))
            } catch (ex: Exception) {
                future.completeExceptionally(CordaRPCAPISenderException("Failed to publish", ex))
                log.error("Failed to publish. Exception: ${ex.message}", ex)
            }
        }

        return future
    }
}

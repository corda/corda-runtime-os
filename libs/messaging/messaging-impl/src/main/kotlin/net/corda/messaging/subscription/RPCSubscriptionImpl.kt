package net.corda.messaging.subscription

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
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
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.config.ResolvedSubscriptionConfig
import net.corda.messaging.constants.MetricsConstants
import net.corda.messaging.utils.ExceptionUtils
import net.corda.metrics.CordaMetrics
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.CompletableFuture

/**
 * RPC subscription implementation utilizing a message bus with producer and consumer to achieve asynchronous
 * request/response processing.
 *
 * On first connection, the subscription goes to the latest message on the topic and not the last one consumed.
 * This means that any requests sent when the response side is not yet operational will not be processed
 * (similar to pub/sub pattern).
 *
 * RPC responses are unreliable so do not use this pattern if reliable response are required.
 */
@Suppress("LongParameterList")
internal class RPCSubscriptionImpl<REQUEST : Any, RESPONSE : Any>(
    private val config: ResolvedSubscriptionConfig,
    private val cordaConsumerBuilder: CordaConsumerBuilder,
    private val producerBuilder: CordaProducerBuilder,
    private val responderProcessor: RPCResponderProcessor<REQUEST, RESPONSE>,
    private val serializer: CordaAvroSerializer<RESPONSE>,
    private val deserializer: CordaAvroDeserializer<REQUEST>,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
) : RPCSubscription<REQUEST, RESPONSE> {

    private val log = LoggerFactory.getLogger("${this.javaClass.name}-${config.clientId}")

    private var threadLooper =
        ThreadLooper(log, config, lifecycleCoordinatorFactory, "rpc subscription thread", ::runConsumeLoop)

    private val errorMsg = "Failed to read records from group ${config.group}, topic ${config.topic}"

    private val processorMeter = CordaMetrics.Metric.Messaging.MessageProcessorTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.RPC_PATTERN_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, config.clientId)
        .withTag(CordaMetrics.Tag.OperationName, MetricsConstants.RPC_RESPONDER_OPERATION)
        .build()

    val isRunning: Boolean
        get() = threadLooper.isRunning

    override val subscriptionName: LifecycleCoordinatorName
        get() = threadLooper.lifecycleCoordinatorName

    override fun start() {
        log.debug { "Starting subscription with config:\n$config" }
        threadLooper.start()
    }

    override fun close() = threadLooper.close()

    private fun runConsumeLoop() {
        var attempts = 0
        while (!threadLooper.loopStopped) {
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
                        threadLooper.updateLifecycleStatus(LifecycleStatus.ERROR, errorMsg)
                        threadLooper.stopLoop()
                    }
                }
            }
        }
    }

    private fun createProducerConsumerAndStartPolling() {
        val producerConfig = ProducerConfig(config.clientId, config.instanceId, false, ProducerRoles.RPC_RESPONDER)
        producerBuilder.createProducer(producerConfig, config.messageBusConfig).use { producer ->
            val consumerConfig = ConsumerConfig(config.group, config.clientId, ConsumerRoles.RPC_RESPONDER)
            cordaConsumerBuilder.createConsumer(
                consumerConfig,
                config.messageBusConfig,
                String::class.java,
                RPCRequest::class.java
            ).use {
                it.subscribe(config.topic)
                threadLooper.updateLifecycleStatus(LifecycleStatus.UP)
                pollAndProcessRecords(it, producer)
            }
        }
    }

    private fun pollAndProcessRecords(consumer: CordaConsumer<String, RPCRequest>, producer: CordaProducer) {
        while (!threadLooper.loopStopped) {
            val consumerRecords = consumer.poll(config.pollTimeout)
            try {
                processRecords(consumerRecords, producer)
            } catch (ex: Exception) {
                // CordaMessageAPIProducerRequiresReset could be thrown here
                when (ex::class.java) {
                    in ExceptionUtils.CordaMessageAPIException -> {
                        throw ex
                    }
                    else -> {
                        throw CordaMessageAPIFatalException(
                            "Failed to process records from topic ${config.topic}, group ${config.group}.",
                            ex,
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
            if (cannotReplyToRequest(it)) {
                log.error("Malformed request cannot be processed and no response can be returned, $it")
                return@forEach
            }

            val rpcRequest = it.value!!
            if (invalidRequest(rpcRequest)) {
                val record = buildRecord(
                    rpcRequest,
                    ResponseStatus.FAILED,
                    ExceptionEnvelope(IllegalArgumentException::javaClass.name, "Invalid RPCRequest").toByteBuffer().array()
                )
                producer.sendRecordsToPartitions(listOf(Pair(rpcRequest.replyPartition, record)))
                return@forEach
            }

            val requestBytes = rpcRequest.payload
            val request = deserializer.deserialize(requestBytes.array())
            val future = CompletableFuture<RESPONSE>()

            future.whenComplete { response, error ->
                val record: CordaProducerRecord<String, RPCResponse>?
                try {
                    when {
                        // the order of these is important due to how the futures api is
                        future.isCancelled -> {
                            record = buildRecord(
                                rpcRequest,
                                ResponseStatus.CANCELLED,
                                ExceptionEnvelope(
                                    error.javaClass.name,
                                    "Future was cancelled"
                                ).toByteBuffer().array()
                            )
                        }
                        future.isCompletedExceptionally -> {
                            record = buildRecord(
                                rpcRequest,
                                ResponseStatus.FAILED,
                                ExceptionEnvelope(error.javaClass.name, error.message).toByteBuffer().array()
                            )
                        }
                        else -> {
                            val serializedResponse = serializer.serialize(response)
                            record = buildRecord(
                                rpcRequest,
                                ResponseStatus.OK,
                                serializedResponse!!
                            )
                        }
                    }
                    producer.sendRecordsToPartitions(listOf(Pair(rpcRequest.replyPartition, record)))
                } catch (ex: Exception) {
                    // intentionally swallowed
                    log.warn("Error publishing response", ex)
                }
            }
            processorMeter.recordCallable { responderProcessor.onNext(request!!, future) }
        }
    }

    private fun cannotReplyToRequest(record: CordaConsumerRecord<String, RPCRequest>): Boolean {
        return record.value == null || record.value?.replyTopic.isNullOrEmpty()
    }

    private fun invalidRequest(rpcRequest: RPCRequest): Boolean {
        return rpcRequest.payload == null || rpcRequest.sender.isNullOrEmpty()
    }

    private fun buildRecord(
        request: RPCRequest,
        status: ResponseStatus,
        payload: ByteArray
    ): CordaProducerRecord<String, RPCResponse> {
        return CordaProducerRecord(
            request.replyTopic,
            request.correlationKey,
            RPCResponse(
                request.sender,
                request.correlationKey,
                Instant.now(),
                status,
                ByteBuffer.wrap(payload)
            )
        )
    }
}

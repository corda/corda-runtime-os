package net.corda.messagebus.kafka.producer

import io.micrometer.core.instrument.binder.MeterBinder
import kotlinx.coroutines.Deferred
import net.corda.messagebus.api.producer.CordaMessage
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messagebus.api.producer.MessageProducer
import net.corda.messagebus.kafka.config.ResolvedProducerConfig
import net.corda.messagebus.kafka.utils.toKafkaRecord
import net.corda.messaging.api.chunking.ChunkSerializerService
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.exception.CordaMessageAPIProducerRequiresReset
import net.corda.metrics.CordaMetrics
import net.corda.tracing.TraceContext
import net.corda.tracing.addTraceContextToRecord
import net.corda.tracing.getOrCreateBatchPublishTracing
import net.corda.tracing.traceSend
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.errors.AuthorizationException
import org.apache.kafka.common.errors.FencedInstanceIdException
import org.apache.kafka.common.errors.InterruptException
import org.apache.kafka.common.errors.InvalidProducerEpochException
import org.apache.kafka.common.errors.ProducerFencedException
import org.apache.kafka.common.errors.TimeoutException
import org.apache.kafka.common.errors.UnsupportedForMessageFormatException
import org.apache.kafka.common.errors.UnsupportedVersionException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class KafkaMessageProducerImpl(
    private val config: ResolvedProducerConfig,
    private val producer: Producer<Any, Any>,
    private val chunkSerializerService: ChunkSerializerService,
    private val producerMetricsBinder: MeterBinder,
) : MessageProducer {
    private val topicPrefix = config.topicPrefix
    private val clientId = config.clientId

    init {
        producerMetricsBinder.bindTo(CordaMetrics.registry)
    }

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val asyncChunkErrorMessage = "Tried to send record which requires chunking using an asynchronous producer"

        val fatalExceptions: Set<Class<out Throwable>> = setOf(
            AuthorizationException::class.java,
            FencedInstanceIdException::class.java,
            ProducerFencedException::class.java,
            UnsupportedForMessageFormatException::class.java,
            UnsupportedVersionException::class.java
        )

        val transientExceptions: Set<Class<out Throwable>> = setOf(
            TimeoutException::class.java,
            InterruptException::class.java,
            // Failure to commit here might be due to consumer kicked from group.
            // Return as intermittent to trigger retry
            InvalidProducerEpochException::class.java,
            // See https://cwiki.apache.org/confluence/display/KAFKA/KIP-588%3A+Allow+producers+to+recover+gracefully+from+transaction+timeouts
            // This exception means the coordinator has bumped the producer epoch because of a timeout of this producer.
            // There is no other producer, we are not a zombie, and so don't need to be fenced, we can simply abort and retry.
            KafkaException::class.java
        )

        val apiExceptions: Set<Class<out Throwable>> = setOf(
            CordaMessageAPIFatalException::class.java,
            CordaMessageAPIIntermittentException::class.java
        )
    }

    private fun toTraceKafkaCallback(callback: MessageProducer.Callback, ctx: TraceContext) : Callback {
        return Callback { m, ex ->
            ctx.markInScope().use {
                ctx.traceTag("send.offset", m.offset().toString())
                ctx.traceTag("send.partition", m.partition().toString())
                ctx.traceTag("send.topic", m.topic())
                callback.onCompletion(ex)
                if (ex != null) {
                    ctx.errorAndFinish(ex)
                } else {
                    ctx.finish()
                }
            }
        }
    }

    override fun send(message: CordaMessage<*>, callback: MessageProducer.Callback?): Deferred<*>? {
        val headers = message.getProperty<Headers>("headers")
        val partition = message.getPropertyOrNull<Int>("partition")

        getOrCreateBatchPublishTracing(clientId).begin(listOf(headers))
        tryWithCleanupOnFailure("send single message") {
            sendMessage(message, partition, callback)
        }

        return null
    }

    override fun sendMessages(messages: List<CordaMessage<*>>) : List<Deferred<*>>? {
        getOrCreateBatchPublishTracing(clientId).begin(messages.map {
            it.getProperty<Headers>("headers")
        })
        tryWithCleanupOnFailure("send multiple message") {
            messages.forEach { message ->
                val partition = message.getPropertyOrNull<Int>("partition")
                sendMessage(message, partition)
            }
        }

        return null
    }

    private fun sendMessage(
        message: CordaMessage<*>,
        partition: Int? = null,
        callback: MessageProducer.Callback? = null
    ) {
        val record = message.toCordaProducerRecord()
        val chunkedRecords = chunkSerializerService.generateChunkedRecords(record)

        if (chunkedRecords.isNotEmpty()) {
            sendChunkedMessage(chunkedRecords, partition, callback)
        } else {
            sendWholeMessage(record, partition, callback)
        }
    }

    // TODO: Producer-level is not supported for non-transactional calls.
    @Suppress("UNUSED_PARAMETER")
    private fun sendChunkedMessage(
        messages: List<CordaProducerRecord<*, *>>,
        partition: Int? = null,
        callback: MessageProducer.Callback? = null
    ) {
        val ex = CordaMessageAPIFatalException(asyncChunkErrorMessage)
        throw ex
    }

    private fun sendWholeMessage(
        record: CordaProducerRecord<*, *>,
        partition: Int? = null,
        callback: MessageProducer.Callback? = null
    ) {
        val traceContext = traceSend(record.headers, "send $clientId")
        traceContext.markInScope().use {
            try {
                producer.send(
                    addTraceContextToRecord(record).toKafkaRecord(topicPrefix, partition),
                    toTraceKafkaCallback({ exception -> callback?.onCompletion(exception) }, traceContext)
                )
            } catch (ex: CordaRuntimeException) {
                traceContext.errorAndFinish(ex)
                val msg = "Failed to send record to topic ${record.topic} with key ${record.key}\""
                if (config.throwOnSerializationError) {
                    log.error(msg, ex)
                    throw ex
                } else {
                    log.warn(msg, ex)
                }
            } catch (ex: Exception) {
                traceContext.errorAndFinish(ex)
                throw ex
            }
        }
    }

    private fun tryWithCleanupOnFailure(
        operation: String,
        block: () -> Unit
    ) {
        try {
            block()
            getOrCreateBatchPublishTracing(config.clientId).complete()
        } catch (ex: Exception) {
            getOrCreateBatchPublishTracing(config.clientId).abort()
            handleException(ex, operation)
        }
    }

    private fun handleException(ex: Exception, operation: String) {
        val errorString = "$operation for CordaKafkaProducer with clientId ${config.clientId}"
        when (ex::class.java) {
            in fatalExceptions -> throw CordaMessageAPIFatalException("FatalError occurred $errorString", ex)
            in transientExceptions -> throw CordaMessageAPIIntermittentException("Error occurred $errorString", ex)
            in apiExceptions -> throw ex
            // TODO: Internally handle recoverable exceptions
            IllegalStateException::class.java -> {
                // It's not clear whether the producer is ok to abort and continue or not in this case, so play it safe
                // and let the client know to create a new one.
                throw CordaMessageAPIProducerRequiresReset("Error occurred $errorString", ex)
            }
            else -> {
                // Here we do not know what the exact cause of the exception is, but we do know Kafka has not told us we
                // must close down, nor has it told us we can abort and retry. In this instance the most sensible thing
                // for the client to do would be to close this producer and create a new one.
                throw CordaMessageAPIProducerRequiresReset("Unexpected error occurred $errorString", ex)
            }
        }
    }

    override fun close() {
        try {
            producer.close()
        } catch (ex: Exception) {
            log.info(
                "CordaKafkaProducer failed to close producer safely. This can be observed when there are " +
                        "no reachable brokers. ClientId: ${config.clientId}", ex
            )
        } finally {
            (producerMetricsBinder as? AutoCloseable)?.close()
        }
    }
}

private fun CordaMessage<*>.toCordaProducerRecord() : CordaProducerRecord<*, *> {
    return CordaProducerRecord(
        topic = this.getProperty<String>("topic"),
        key = this.getProperty("key"),
        value = this.payload,
        headers = this.getProperty<Headers>("headers")
    )
}

private typealias Headers = List<Pair<String, String>>

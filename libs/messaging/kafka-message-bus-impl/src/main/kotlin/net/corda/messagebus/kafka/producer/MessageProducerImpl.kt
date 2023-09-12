package net.corda.messagebus.kafka.producer

import io.micrometer.core.instrument.binder.MeterBinder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import net.corda.messagebus.api.producer.CordaMessage
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messagebus.api.producer.MessageProducer
import net.corda.metrics.CordaMetrics
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MessageProducerImpl(
    private val clientId: String,
    private val producer: CordaProducer,
    private val producerMetricsBinder: MeterBinder,
) : MessageProducer {
    init {
        producerMetricsBinder.bindTo(CordaMetrics.registry)
    }

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun send(message: CordaMessage<*>): Deferred<CordaMessage<*>?> =
        CompletableDeferred<CordaMessage<*>>().apply {
            producer.send(message.toCordaProducerRecord()) { ex ->
                ex?.let { completeExceptionally(ex) }
            }
        }

    override fun close() {
        try {
            producer.close()
        } catch (ex: Exception) {
            log.info(
                "MessageProducerImpl failed to close producer safely. This can be observed when there are " +
                        "no reachable brokers. ClientId: $clientId", ex
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
        headers = this.getProperty<Headers>("headers"),
    )
}

private typealias Headers = List<Pair<String, String>>

package net.corda.otel.service;

import io.opentelemetry.context.propagation.TextMapGetter
import java.nio.charset.StandardCharsets
import java.util.stream.Collectors
import java.util.stream.StreamSupport
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.Header

enum class KafkaConsumerRecordGetter : TextMapGetter<ConsumerRecord<*, *>> {
    INSTANCE;

    override fun keys(carrier: ConsumerRecord<*, *>): Iterable<String> {
        return StreamSupport.stream(carrier.headers().spliterator(), false)
            .map { obj: Header -> obj.key() }
            .collect(Collectors.toList())
    }

    override fun get(carrier: ConsumerRecord<*, *>?, key: String): String? {
        val header = carrier!!.headers().lastHeader(key) ?: return null
        val value = header.value() ?: return null
        return String(value, StandardCharsets.UTF_8)
    }
}
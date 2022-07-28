package net.corda.otel.service;

import io.opentelemetry.context.propagation.TextMapSetter
import java.nio.charset.StandardCharsets
import org.apache.kafka.common.header.Headers

enum class KafkaHeadersSetter : TextMapSetter<Headers> {
    INSTANCE;

    override fun set(headers: Headers?, key: String, value: String) {
        headers!!.remove(key).add(key, value.toByteArray(StandardCharsets.UTF_8))
    }
}
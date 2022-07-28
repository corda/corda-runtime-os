package net.corda.otel.service

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.context.propagation.TextMapPropagator

interface OpenTelemetryService {
    fun getOpenTelemetryInstance() : OpenTelemetry
    fun getKafkaHeaderSetter() : KafkaHeadersSetter
    fun getKafkaHeaderGetter() : KafkaConsumerRecordGetter
    fun getTextMapPropagator() : TextMapPropagator
}
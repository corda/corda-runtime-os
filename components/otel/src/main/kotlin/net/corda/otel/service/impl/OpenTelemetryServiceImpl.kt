package net.corda.otel.service.impl

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.context.propagation.TextMapPropagator
import io.opentelemetry.exporter.logging.LoggingMetricExporter
import io.opentelemetry.exporter.logging.LoggingSpanExporter
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import java.util.concurrent.TimeUnit
import net.corda.otel.service.KafkaConsumerRecordGetter
import net.corda.otel.service.KafkaHeadersSetter
import net.corda.otel.service.OpenTelemetryService
import org.osgi.service.component.annotations.Component


@Component(service = [OpenTelemetryService::class])
class OpenTelemetryServiceImpl : OpenTelemetryService {

    private var openTelemetry: OpenTelemetry? = null

    override fun getOpenTelemetryInstance(): OpenTelemetry {
        val openTelemetryInstance = openTelemetry
        return if (openTelemetryInstance != null) {
            openTelemetryInstance
        } else {

            val httpSpanExporter = OtlpHttpSpanExporter.builder()
                .setEndpoint("http://tempo:4318/v1/traces")
                .setTimeout(30, TimeUnit.SECONDS)
                .build()

            val sdkTracerProvider: SdkTracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(httpSpanExporter).build())
                .addSpanProcessor(BatchSpanProcessor.builder(LoggingSpanExporter.create()).build())
                .build()


            val httpMetricExporter = OtlpHttpMetricExporter.builder()
                .setEndpoint("http://tempo:4318/v1/metrics")
                .setTimeout(30, TimeUnit.SECONDS)
                .build()

            val sdkMeterProvider: SdkMeterProvider = SdkMeterProvider.builder()
                .registerMetricReader(PeriodicMetricReader.builder(httpMetricExporter).build())
                .registerMetricReader(PeriodicMetricReader.builder(LoggingMetricExporter.create()).build())
                .build()

            val openTelemetry: OpenTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setMeterProvider(sdkMeterProvider)
                 //this propagator is necessary
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build()

            this.openTelemetry = openTelemetry
            openTelemetry
        }
    }

    override fun getKafkaHeaderSetter(): KafkaHeadersSetter {
        return KafkaHeadersSetter.INSTANCE
    }

    override fun getKafkaHeaderGetter(): KafkaConsumerRecordGetter {
        return KafkaConsumerRecordGetter.INSTANCE
    }

    override fun getTextMapPropagator(): TextMapPropagator {
        return getOpenTelemetryInstance().propagators.textMapPropagator
    }
}

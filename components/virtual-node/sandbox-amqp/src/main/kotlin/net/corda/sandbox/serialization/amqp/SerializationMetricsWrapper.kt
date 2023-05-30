package net.corda.sandbox.serialization.amqp

import io.micrometer.core.instrument.Timer
import net.corda.metrics.CordaMetrics
import net.corda.metrics.recordOptionally
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.serialization.SerializedBytes

class SerializationMetricsWrapper(
    private val serializationService: SerializationService,
    sandboxGroupContext: SandboxGroupContext
) : SerializationService {
    private val holdingId = sandboxGroupContext.virtualNodeContext.holdingIdentity.shortHash.toString()

    override fun <T : Any> serialize(obj: T): SerializedBytes<T> {
        return CordaMetrics.Metric.Serialization.SerializationTime
            .builder()
            .forVirtualNode(holdingId)
            .withTag(CordaMetrics.Tag.SerializedClass, obj::class.java.name)
            .build()
            .recordOptionally(greaterThanMillis = 1) { serializationService.serialize(obj) }
    }

    override fun <T : Any> deserialize(serializedBytes: SerializedBytes<T>, clazz: Class<T>): T {
        return deserializationTime(clazz).recordOptionally(greaterThanMillis = 1) {
            serializationService.deserialize(serializedBytes, clazz)
        }
    }

    override fun <T : Any> deserialize(bytes: ByteArray, clazz: Class<T>): T {
        return deserializationTime(clazz).recordOptionally(greaterThanMillis = 1) {
            serializationService.deserialize(bytes, clazz)
        }
    }

    private fun deserializationTime(clazz: Class<*>): Timer {
        return CordaMetrics.Metric.Serialization.DeserializationTime
            .builder()
            .forVirtualNode(holdingId)
            .withTag(CordaMetrics.Tag.SerializedClass, clazz.name)
            .build()
    }
}

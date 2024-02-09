package net.corda.sandbox.serialization.amqp

import io.micrometer.core.instrument.Timer
import net.corda.internal.serialization.amqp.api.SerializationServiceInternal
import net.corda.metrics.CordaMetrics
import net.corda.metrics.recordOptionally
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.serialization.SerializedBytes

class SerializationMetricsWrapper(
    private val serializationService: SerializationServiceInternal,
    sandboxGroupContext: SandboxGroupContext
) : SerializationServiceInternal {
    private val holdingId = sandboxGroupContext.virtualNodeContext.holdingIdentity.shortHash.toString()

    override fun <T : Any> serialize(obj: T): SerializedBytes<T> {
        return serializationTime(obj).recordOptionally(greaterThanMillis = 1) {
            serializationService.serialize(obj)
        }
    }

    override fun <T : Any> serialize(obj: T, withCompression: Boolean): SerializedBytes<T> {
        return serializationTime(obj).recordOptionally(greaterThanMillis = 1) {
            serializationService.serialize(obj, withCompression)
        }
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

    private fun serializationTime(obj: Any): Timer {
        return CordaMetrics.Metric.Serialization.SerializationTime
            .builder()
            .forVirtualNode(holdingId)
            .withTag(CordaMetrics.Tag.SerializedClass, obj::class.java.name)
            .build()
    }

    private fun deserializationTime(clazz: Class<*>): Timer {
        return CordaMetrics.Metric.Serialization.DeserializationTime
            .builder()
            .forVirtualNode(holdingId)
            .withTag(CordaMetrics.Tag.SerializedClass, clazz.name)
            .build()
    }
}

package net.corda.sandbox.serialization.amqp

import io.micrometer.core.instrument.Timer
import net.corda.metrics.CordaMetrics
import net.corda.metrics.recordOptionally
import net.corda.sandbox.type.UsedByPersistence
import net.corda.sandbox.type.UsedByVerification
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.serialization.SerializedBytes
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

/**
 * A [SerializationService] component for persistence and verification sandboxes.
 * Flow sandboxes already have a special "fiber-aware" component of their own.
 */
@Component(
    service = [
        SerializationService::class,
        SerializationServiceProxy::class,
        UsedByPersistence::class,
        UsedByVerification::class
    ],
    scope = PROTOTYPE
)
class SerializationServiceProxy @Activate constructor(
    @Reference(service = CurrentSandboxGroupContext::class)
    private val currentSandboxGroupContext: CurrentSandboxGroupContext
) : SerializationService, UsedByPersistence, UsedByVerification {
    private var serializationService: SerializationService? = null

    fun wrap(serializer: SerializationService) {
        serializationService = serializer
    }

    override fun <T : Any> deserialize(bytes: ByteArray, clazz: Class<T>): T {
        return checkNotNull(serializationService) { "deserialize(byte[], Class): Not initialised" }
            .run {
                deserializationTime(clazz).recordOptionally(greaterThanMillis = 1) {
                    deserialize(bytes, clazz)
                }
            }
    }

    override fun <T : Any> deserialize(serializedBytes: SerializedBytes<T>, clazz: Class<T>): T {
        return checkNotNull(serializationService) { "deserialize(SerializedBytes, Class): Not initialized" }
            .run {
                deserializationTime(clazz).recordOptionally(greaterThanMillis = 1) {
                    deserialize(serializedBytes, clazz)
                }
            }
    }

    override fun <T : Any> serialize(obj: T): SerializedBytes<T> {
        return checkNotNull(serializationService) { "serialize(Object): Not initialized" }
            .run {
                CordaMetrics.Metric.Serialization.SerializationTime
                    .builder()
                    .forVirtualNode(currentSandboxGroupContext.get().virtualNodeContext.holdingIdentity.shortHash.toString())
                    .withTag(CordaMetrics.Tag.SerializedClass, obj::class.java.name)
                    .build()
                    .recordOptionally(greaterThanMillis = 1) { serialize(obj) }
            }
    }

    private fun deserializationTime(clazz: Class<*>): Timer {
        return CordaMetrics.Metric.Serialization.DeserializationTime
            .builder()
            .forVirtualNode(currentSandboxGroupContext.get().virtualNodeContext.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.SerializedClass, clazz.name)
            .build()
    }
}

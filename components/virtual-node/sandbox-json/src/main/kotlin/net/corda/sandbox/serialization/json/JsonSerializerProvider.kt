package net.corda.sandbox.serialization.json

import net.corda.common.json.serializers.SerializationCustomizer
import net.corda.common.json.serializers.serializableClassFromJsonSerializer
import net.corda.sandbox.type.SandboxConstants.CORDA_MARKER_ONLY_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByPersistence
import net.corda.sandbox.type.UsedByVerification
import net.corda.sandboxgroupcontext.CustomMetadataConsumer
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.getSandboxSingletonServices
import net.corda.sandboxgroupcontext.getMetadataServices
import net.corda.v5.application.marshalling.json.JsonDeserializer
import net.corda.v5.application.marshalling.json.JsonSerializer
import net.corda.v5.base.util.loggerFor
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceScope
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

/**
 * Configures a sandbox with handwritten JSON serializers and deserializers.
 * These can either be supplied by the platform or imported from CPBs.
 */
@Suppress("unused")
@Component(
    service = [ UsedByFlow::class, UsedByPersistence::class, UsedByVerification::class ],
    property = [ CORDA_MARKER_ONLY_SERVICE ],
    scope = PROTOTYPE
)
class JsonSerializerProvider @Activate constructor(
    @Reference(service = JsonSerializer::class, scope = ReferenceScope.PROTOTYPE)
    private val internalJsonSerializers: List<JsonSerializer<*>>,
    @Reference(service = JsonDeserializer::class, scope = ReferenceScope.PROTOTYPE)
    private val internalJsonDeserializers: List<JsonDeserializer<*>>
) : UsedByFlow, UsedByPersistence, UsedByVerification, CustomMetadataConsumer {
    private companion object {
        private val logger = loggerFor<JsonSerializerProvider>()
    }

    override fun accept(context: MutableSandboxGroupContext) {
        val customizers = context.getSandboxSingletonServices<SerializationCustomizer>()

        registerJsonSerializers(customizers, context)
        registerJsonDeserializers(customizers, context)
    }

    private fun registerJsonSerializers(customizers: Set<SerializationCustomizer>, context: MutableSandboxGroupContext) {
        // Kotlin's Set.plus(Set) operator function preserves iteration order.
        // We add the platform's serializers first, and the users' second.
        val cordappJsonSerializers = context.getMetadataServices<JsonSerializer<*>>()
        (internalJsonSerializers + cordappJsonSerializers).forEach { serializer ->
            val targetClass = try {
                serializableClassFromJsonSerializer(serializer)
            } catch (e: Exception) {
                logger.warn("Failed to extract serialization type from {}: {}", serializer::class.java.name, e.message)
                return@forEach
            }
            customizers.forEach { customizer ->
                logger.trace("Registering JSON serializer {} for {}", serializer::class.java.name, targetClass.name)
                if (!customizer.setSerializer(serializer, targetClass)) {
                    logger.warn("Failed to register {}", serializer::class.java.name)
                }
            }
        }
    }

    private fun registerJsonDeserializers(customizers: Set<SerializationCustomizer>, context: MutableSandboxGroupContext) {
        // Kotlin's Set.plus(Set) operator function preserves iteration order.
        // We add the platform's deserializers first, and the users' second.
        val cordappJsonDeserializers = context.getMetadataServices<JsonDeserializer<*>>()
        (internalJsonDeserializers + cordappJsonDeserializers).forEach { deserializer ->
            val targetClass = try {
                serializableClassFromJsonSerializer(deserializer)
            } catch (e: Exception) {
                logger.warn("Failed to extract deserialization type from {}: {}", deserializer::class.java.name, e.message)
                return@forEach
            }
            customizers.forEach { customizer ->
                logger.trace("Registering JSON deserializer {} for {}", deserializer::class.java.name, targetClass.name)
                if (!customizer.setDeserializer(deserializer, targetClass)) {
                    logger.warn("Failed to register {}", deserializer::class.java.name)
                }
            }
        }
    }
}

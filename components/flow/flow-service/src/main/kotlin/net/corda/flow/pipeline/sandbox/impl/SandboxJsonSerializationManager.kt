package net.corda.flow.pipeline.sandbox.impl

import net.corda.common.json.serializers.SerializationCustomizer
import net.corda.common.json.serializers.serializableClassNameFromJsonSerializer
import net.corda.sandbox.SandboxGroup
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.v5.application.marshalling.json.JsonDeserializer
import net.corda.v5.application.marshalling.json.JsonSerializer
import java.lang.Exception

/**
 * Manages the details of applying Json serializers and deserializers to a sandbox context.
 */
class SandboxJsonSerializationManager(
    val sandboxGroupContext: MutableSandboxGroupContext,
    private val serializationCustomizer: SerializationCustomizer
) {
    private val sandboxGroup: SandboxGroup = sandboxGroupContext.sandboxGroup

    /**
     * Will push any errors to failureBlock including exceptions thrown when extracting serializing type.
     * Will propagate any thrown exceptions out of failureBlock, so it is up to the caller to decide how to handle the
     * errors.
     */
    fun setDeserializer(jsonDeserializer: JsonDeserializer<*>, failureBlock: (String) -> Unit) {
        val clazz = try {
            extractJsonSerializingType(jsonDeserializer)
        } catch (e: Exception) {
            failureBlock("Failed to extract serialization type from deserializer: " + e.message)
            return
        }

        if (!serializationCustomizer.setDeserializer(jsonDeserializer, clazz)) {
            failureBlock(
                "Failed to register for type ${clazz.canonicalName}, likely a deserializer was already " +
                        "registered for this type."
            )
        }
    }

    /**
     * Will push any errors to failureBlock including exceptions thrown when extracting serializing type.
     * Will propagate any thrown exceptions out of failureBlock, so it is up to the caller to decide how to handle the
     * errors.
     */
    fun setSerializer(jsonSerializer: JsonSerializer<*>, failureBlock: (String) -> Unit) {
        val clazz = try {
            extractJsonSerializingType(jsonSerializer)
        } catch (e: Exception) {
            failureBlock("Failed to extract serialization type from serializer: " + e.message)
            return
        }

        if (!serializationCustomizer.setSerializer(jsonSerializer, clazz)) {
            failureBlock(
                "Failed to register for type ${clazz.canonicalName}, likely a serializer was already " +
                        "registered for this type."
            )
        }
    }

    private inline fun <reified T : Any> extractJsonSerializingType(jsonSerializer: T): Class<*> {
        val className = serializableClassNameFromJsonSerializer(jsonSerializer)

        return try {
            // Try to find the target type for serialization in the default class loaders
            Class.forName(className)
        } catch (e: ClassNotFoundException) {
            // Otherwise look for it in the bundles
            sandboxGroup.loadClassFromMainBundles(className, Any::class.java)
        }
    }
}

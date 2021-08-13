package net.corda.serialization

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.types.ByteSequence
import net.corda.v5.serialization.CheckpointCustomSerializer
import net.corda.v5.serialization.ClassWhitelist
import net.corda.v5.serialization.EncodingWhitelist
import net.corda.v5.serialization.SerializationEncoding
import java.io.NotSerializableException

@DoNotImplement
interface CheckpointSerializer {
    @Throws(NotSerializableException::class)
    fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: CheckpointSerializationContext): T

    @Throws(NotSerializableException::class)
    fun <T : Any> serialize(obj: T, context: CheckpointSerializationContext): ByteArray
}

/**
 * Parameters to checkpoint serialization and deserialization.
 */
@DoNotImplement
interface CheckpointSerializationContext {
    /**
     * If non-null, apply this encoding (typically compression) when serializing.
     */
    val encoding: SerializationEncoding?

    /**
     * The class loader to use for deserialization (for classes not in a bundle).
     */
    val deserializationClassLoader: ClassLoader

    /**
     * A whitelist that contains (mostly for security purposes) which classes can be serialized and deserialized.
     */
    val whitelist: ClassWhitelist

    /**
     * A whitelist that determines (mostly for security purposes) whether a particular encoding may be used when
     * deserializing.
     */
    val encodingWhitelist: EncodingWhitelist

    /**
     * A map of any addition properties specific to the particular use case.
     */
    val properties: Map<Any, Any>

    /**
     * Duplicate references to the same object preserved in the wire format and when deserialized when this is true,
     * otherwise they appear as new copies of the object.
     */
    val objectReferencesEnabled: Boolean

    /**
     * User defined custom serializers for use in checkpoint serialization.
     */
    val checkpointCustomSerializers: Iterable<CheckpointCustomSerializer<*, *>>

    /**
     * Service used to retrieve information about CPKs from the context of the current sandbox.
     */
    val classInfoService: Any?

    /**
     * The set of CorDapp sandboxes for the node's CPI.
     *
     * In the future, we will allow multiple CPIs per node, and thus we will need to support multiple sandbox groups.
     */
    val sandboxGroup: Any?

    /**
     * Helper method to set the ClassInfoService
     */
    fun withClassInfoService(classInfoService: Any): CheckpointSerializationContext

    /**
     * Helper method to set the SandboxGroup
     */
    fun withSandboxGroup(sandboxGroup: Any): CheckpointSerializationContext

    /**
     * Helper method to return a new context based on this context with the property added.
     */
    fun withProperty(property: Any, value: Any): CheckpointSerializationContext

    /**
     * Helper method to return a new context based on this context with object references disabled.
     */
    fun withoutReferences(): CheckpointSerializationContext

    /**
     * Helper method to return a new context based on this context with the deserialization class loader changed.
     */
    fun withClassLoader(classLoader: ClassLoader): CheckpointSerializationContext

    /**
     * Helper method to return a new context based on this context with the given class specifically whitelisted.
     */
    fun withWhitelisted(clazz: Class<*>): CheckpointSerializationContext

    /**
     * A shallow copy of this context but with the given (possibly null) encoding.
     */
    fun withEncoding(encoding: SerializationEncoding?): CheckpointSerializationContext

    /**
     * A shallow copy of this context but with the given encoding whitelist.
     */
    fun withEncodingWhitelist(encodingWhitelist: EncodingWhitelist): CheckpointSerializationContext

    /**
     * A shallow copy of this context but with the given custom serializers.
     */
    fun withCheckpointCustomSerializers(checkpointCustomSerializers: Iterable<CheckpointCustomSerializer<*, *>>):
            CheckpointSerializationContext
}
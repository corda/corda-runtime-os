@file:JvmName("Serialization")
package net.corda.internal.serialization

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.types.ByteSequence
import net.corda.v5.serialization.SerializationCustomSerializer
import net.corda.v5.serialization.SerializedBytes

data class ObjectWithCompatibleContext<out T : Any>(val obj: T, val context: SerializationContext)

/**
 * An abstraction for serializing and deserializing objects, with support for versioning of the wire format via
 * a header / prefix in the bytes.
 */
interface SerializationFactory {
    /**
     * Deserialize the bytes in to an object, using the prefixed bytes to determine the format.
     *
     * @param byteSequence The bytes to deserialize, including a format header prefix.
     * @param clazz The class or superclass or the object to be deserialized, or [Any] or [Object] if unknown.
     * @param context A context that configures various parameters to deserialization.
     */
    fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): T

    /**
     * Deserialize the bytes in to an object, using the prefixed bytes to determine the format.
     *
     * @param byteSequence The bytes to deserialize, including a format header prefix.
     * @param clazz The class or superclass or the object to be deserialized, or [Any] or [Object] if unknown.
     * @param context A context that configures various parameters to deserialization.
     * @return deserialized object along with [SerializationContext] to identify encoding used.
     */
    fun <T : Any> deserializeWithCompatibleContext(
        byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext
    ): ObjectWithCompatibleContext<T>

    /**
     * Serialize an object to bytes using the preferred serialization format version from the context.
     *
     * @param obj The object to be serialized.
     * @param context A context that configures various parameters to serialization,
     * including the serialization format version.
     */
    fun <T : Any> serialize(obj: T, context: SerializationContext): SerializedBytes<T>
}

typealias SerializationMagic = ByteSequence

@DoNotImplement
interface SerializationEncoding

/**
 * Parameters to serialization and deserialization.
 */
@DoNotImplement
interface SerializationContext {
    /**
     * When serializing, use the format this header sequence represents.
     */
    val preferredSerializationVersion: SerializationMagic
    /**
     * If non-null, apply this encoding (typically compression) when serializing.
     */
    val encoding: SerializationEncoding?
    /**
     * The class loader to use for deserialization.
     */
    val deserializationClassLoader: ClassLoader
    /**
     * A whitelist that contains (mostly for security purposes) which classes can be serialized and deserialized.
     */
    val whitelist: ClassWhitelist
    /**
     * A whitelist that determines (mostly for security purposes)
     * whether a particular encoding may be used when deserializing.
     */
    val encodingWhitelist: EncodingWhitelist
    /**
     * A map of any addition properties specific to the particular use case.
     */
    val properties: Map<Any, Any>
    /**
     * Duplicate references to the same object preserved in the wire format and when
     * deserialized when this is true, otherwise they appear as new copies of the object.
     */
    val objectReferencesEnabled: Boolean
    /**
     * If true the serialization evolver will fail if the binary to be deserialized
     * contains more fields then the current object from the classpath.
     *
     * The default is false.
     */
    val preventDataLoss: Boolean
    /**
     * The use case we are serializing or deserializing for.  See [UseCase].
     */
    val useCase: UseCase
    /**
     * Custom serializers that will be made available during (de)serialization.
     * If this is null then the default Custom Serializers will be used.
     */
    val customSerializers: Set<SerializationCustomSerializer<*, *>>?
    /**
     * Service used to retrieve information about CPKs from the context of the current sandbox.
     */
    val classInfoService: Any?
    /**
     * The set of CorDapp sandboxes for the node's CPB.
     *
     * In the future, we will allows multiple CPBs per node, and thus we will need
     * to support multiple sandbox groups.
     */
    val sandboxGroup: Any?

    /**
     * Helper method to set the ClassInfoService
     */
    fun withClassInfoService(classInfoService: Any): SerializationContext

    /**
     * Helper method to set the SandboxGroup
     */
    fun withSandboxGroup(sandboxGroup: Any): SerializationContext

    /**
     * Helper method to return a new context based on this context with the property added.
     */
    fun withProperty(property: Any, value: Any): SerializationContext

    /**
     * Helper method to return a new context based on this context with object references disabled.
     */
    fun withoutReferences(): SerializationContext

    /**
     * Return a new context based on this one but with a strict evolution.
     * @see preventDataLoss
     */
    fun withPreventDataLoss(): SerializationContext

    /**
     * Helper method to return a new context based on this context with the deserialization class loader changed.
     */
    fun withClassLoader(classLoader: ClassLoader): SerializationContext

    /**
     * Helper method to return a new context based on this context with the given class specifically whitelisted.
     */
    fun withWhitelisted(clazz: Class<*>): SerializationContext

    /**
     * Helper method to return a new context based on this context with the given serializers added.
     */
    fun withCustomSerializers(serializers: Set<SerializationCustomSerializer<*, *>>): SerializationContext

    /**
     * Helper method to return a new context based on this context but with serialization
     * using the format this header sequence represents.
     */
    fun withPreferredSerializationVersion(magic: SerializationMagic): SerializationContext

    /**
     * A shallow copy of this context but with the given (possibly null) encoding.
     */
    fun withEncoding(encoding: SerializationEncoding?): SerializationContext

    /**
     * A shallow copy of this context but with the given encoding whitelist.
     */
    fun withEncodingWhitelist(encodingWhitelist: EncodingWhitelist): SerializationContext

    /**
     * The use case that we are serializing for, since it influences the implementations chosen.
     */
    enum class UseCase {
        P2P,
        Storage,
        Testing
    }
}

/**
 * Set of well known properties that may be set on a serialization context. This doesn't preclude
 * others being set that aren't keyed on this enumeration, but for general use properties adding a
 * well known key here is preferred.
 */
enum class ContextPropertyKeys {
    SERIALIZERS
}

interface ClassWhitelist {
    fun hasListed(type: Class<*>): Boolean
}

@DoNotImplement
interface EncodingWhitelist {
    fun acceptEncoding(encoding: SerializationEncoding): Boolean
}

/**
 * Helper method to return a new context based on this context with the
 * given list of classes specifically whitelisted.
 */
fun SerializationContext.withWhitelist(classes: List<Class<*>>): SerializationContext {
    var currentContext = this
    classes.forEach {
            clazz -> currentContext = currentContext.withWhitelisted(clazz)
    }

    return currentContext
}

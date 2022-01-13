package net.corda.serialization

import net.corda.v5.serialization.SerializationCustomSerializer

/**
 * Parameters to serialization and deserialization.
 */
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
     * The set of CorDapp sandboxes for the node's CPB.
     *
     * In the future, we will allows multiple CPBs per node, and thus we will need
     * to support multiple sandbox groups.
     */
    val sandboxGroup: Any?

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

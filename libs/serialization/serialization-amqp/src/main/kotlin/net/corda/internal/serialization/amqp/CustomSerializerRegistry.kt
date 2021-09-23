package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.model.DefaultCacheProvider
import net.corda.internal.serialization.model.TypeIdentifier
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaThrowable
import net.corda.v5.base.util.uncheckedCast
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import net.corda.v5.serialization.SerializationCustomSerializer
import net.corda.v5.serialization.SingletonSerializeAsToken
import java.io.NotSerializableException
import java.lang.reflect.Type

/**
 * Thrown when a [CustomSerializer] offers to serialize a type for which custom serialization is not permitted, because
 * it should be handled by standard serialisation methods (or not serialised at all) and there is no valid use case for
 * a custom method.
 */
class IllegalCustomSerializerException(customSerializer: AMQPSerializer<*>, clazz: Class<*>) :
        Exception("Custom serializer ${customSerializer::class.qualifiedName} registered " +
                "to serialize non-custom-serializable type $clazz")

/**
 * Thrown when more than one [CustomSerializer] offers to serialize the same type, which may indicate a malicious attempt
 * to override already-defined behaviour.
 */
class DuplicateCustomSerializerException(serializers: List<AMQPSerializer<*>>, clazz: Class<*>) :
        Exception("Multiple custom serializers " + serializers.map { it::class.qualifiedName } +
                " registered to serialize type $clazz")

interface CustomSerializerRegistry {

    /**
     * Retrieves the names of the registered custom serializers.
     */
    val customSerializerNames: List<String>

    /**
     * Register a custom serializer for any type that cannot be serialized or deserialized by the default serializer
     * that expects to find getters and a constructor with a parameter for each property.
     */
    fun register(customSerializer: CustomSerializer<out Any>)
    fun register(customSerializer: SerializationCustomSerializer<*,*>)
    fun registerExternal(customSerializer: CorDappCustomSerializer)
    fun registerExternal(customSerializer: SerializationCustomSerializer<*,*>)

    /**
     * Try to find a custom serializer for the actual class, and declared type, of a value.
     *
     * @param clazz The actual class to look for a custom serializer for.
     * @param declaredType The declared type to look for a custom serializer for.
     * @return The custom serializer handling the class, if found, or `null`.
     *
     * @throws IllegalCustomSerializerException If a custom serializer identifies itself as the serializer for
     * a class annotated with [CordaSerializable], since all such classes should be serializable via standard object
     * serialization.
     *
     * @throws DuplicateCustomSerializerException If more than one custom serializer identifies itself as the serializer
     * for the given class, as this creates an ambiguous situation.
     */
    fun findCustomSerializer(clazz: Class<*>, declaredType: Type): AMQPSerializer<Any>?
}

class CachingCustomSerializerRegistry(
        private val descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry,
        private val allowedFor: Set<Class<*>>
) : CustomSerializerRegistry {
    constructor(descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry) : this(descriptorBasedSerializerRegistry, emptySet())

    companion object {
        val logger = contextLogger()
    }

    override val customSerializerNames: List<String>
        get() = customSerializers.map { serializer ->
            if (serializer is CorDappCustomSerializer) serializer.toString()
            else "${serializer::class.java} - Classloader: ${serializer::class.java.classLoader}"
        }

    private data class CustomSerializerIdentifier(val actualTypeIdentifier: TypeIdentifier, val declaredTypeIdentifier: TypeIdentifier)

    private sealed class CustomSerializerLookupResult {

        abstract val serializerIfFound: AMQPSerializer<Any>?

        object None : CustomSerializerLookupResult() {
            override val serializerIfFound: AMQPSerializer<Any>? = null
        }

        data class CustomSerializerFound(override val serializerIfFound: AMQPSerializer<Any>) : CustomSerializerLookupResult()
    }

    private val customSerializersCache: MutableMap<CustomSerializerIdentifier, CustomSerializerLookupResult> = DefaultCacheProvider.createCache()
    private val customSerializers: MutableList<SerializerFor> = mutableListOf()

    /**
     * Register a custom serializer for any type that cannot be serialized or deserialized by the default serializer
     * that expects to find getters and a constructor with a parameter for each property.
     */
    override fun register(customSerializer: CustomSerializer<out Any>) {
        logger.trace { "action=\"Registering custom serializer\", class=\"${customSerializer.type}\"" }

        checkActiveCache(customSerializer.type)

        descriptorBasedSerializerRegistry.getOrBuild(customSerializer.typeDescriptor.toString()) {
            customSerializers += customSerializer
            for (alias in customSerializer.deserializationAliases) {
                val aliasDescriptor = typeDescriptorFor(alias)
                if (aliasDescriptor != customSerializer.typeDescriptor) {
                    descriptorBasedSerializerRegistry[aliasDescriptor.toString()] = customSerializer
                }
            }

            customSerializer
        }

    }

    override fun register(customSerializer: SerializationCustomSerializer<*, *>) {
        TODO("Not yet implemented")
    }

    override fun registerExternal(customSerializer: CorDappCustomSerializer) {
        logger.trace { "action=\"Registering external serializer\", class=\"${customSerializer.type}\"" }

        checkActiveCache(customSerializer.type)

        descriptorBasedSerializerRegistry.getOrBuild(customSerializer.typeDescriptor.toString()) {
            customSerializers += customSerializer
            customSerializer
        }
    }

    private fun checkActiveCache(type: Type) {
        if (customSerializersCache.isNotEmpty()) {
            logger.warn(
                "Attempting to register custom serializer $type in an active cache. " +
                        "All serializers must be registered before the cache comes into use."
            )
        }
    }

    override fun registerExternal(customSerializer: SerializationCustomSerializer<*, *>) {
        registerExternal(CorDappCustomSerializer(customSerializer, false))
    }

    override fun findCustomSerializer(clazz: Class<*>, declaredType: Type): AMQPSerializer<Any>? {
        val typeIdentifier = CustomSerializerIdentifier(
                TypeIdentifier.forClass(clazz),
                TypeIdentifier.forGenericType(declaredType))

        return customSerializersCache.getOrPut(typeIdentifier) {
                val customSerializer = doFindCustomSerializer(clazz, declaredType)
                if (customSerializer == null) CustomSerializerLookupResult.None
                else CustomSerializerLookupResult.CustomSerializerFound(customSerializer)
        }.serializerIfFound
    }

    @Suppress("ThrowsCount", "ComplexMethod")
    private fun doFindCustomSerializer(clazz: Class<*>, declaredType: Type): AMQPSerializer<Any>? {
        val declaredSuperClass = declaredType.asClass().superclass

        val declaredSerializers = customSerializers.mapNotNull { customSerializer ->
            when {
                !customSerializer.isSerializerFor(clazz) -> null
                (declaredSuperClass == null
                        || !customSerializer.isSerializerFor(declaredSuperClass)
                        || !customSerializer.revealSubclassesInSchema) -> {
                    logger.debug { "action=\"Using custom serializer\", class=${clazz.typeName}, declaredType=${declaredType.typeName}" }

                    @Suppress("UNCHECKED_CAST")
                    customSerializer as? AMQPSerializer<Any>
                }
                else ->
                    // Make a subclass serializer for the subclass and return that...
                    CustomSerializer.SubClass(clazz, uncheckedCast(customSerializer))
            }
        }

        if (declaredSerializers.isEmpty()) {
            if (SingletonSerializeAsToken::class.java.isAssignableFrom(clazz)) {
                throw NotSerializableException("Attempt to serialise SingletonSerializeAsToken")
            } else {
                return null
            }
        }
        if (declaredSerializers.size > 1) {
            logger.warn("Duplicate custom serializers detected for $clazz: ${declaredSerializers.map { it::class.qualifiedName }}")
            throw DuplicateCustomSerializerException(declaredSerializers, clazz)
        }
        if (clazz.isCustomSerializationForbidden) {
            logger.warn("Illegal custom serializer detected for $clazz: ${declaredSerializers.first()::class.qualifiedName}")
            throw IllegalCustomSerializerException(declaredSerializers.first(), clazz)
        }

        return declaredSerializers.first().let {
            if (it is CustomSerializer<Any>) {
                it.specialiseFor(declaredType)
            } else {
                it
            }
        }
    }

    private val Class<*>.isCustomSerializationForbidden: Boolean get() = when {
        AMQPTypeIdentifiers.isPrimitive(this) -> true
        isSubClassOf(CordaThrowable::class.java) -> false
        allowedFor.any { it.isAssignableFrom(this) } -> false
        isAnnotationPresent(CordaSerializable::class.java) -> true
        else -> false
    }
}


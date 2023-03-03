package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.amqp.CustomSerializerPermission.Companion.NON_BUNDLE
import net.corda.internal.serialization.amqp.standard.CorDappCustomSerializer
import net.corda.internal.serialization.amqp.standard.CustomSerializer
import net.corda.internal.serialization.model.DefaultCacheProvider
import net.corda.internal.serialization.model.TypeIdentifier
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.InternalCustomSerializer
import net.corda.serialization.InternalDirectSerializer
import net.corda.serialization.InternalProxySerializer
import net.corda.utilities.debug
import net.corda.utilities.trace
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.exceptions.CordaThrowable
import net.corda.v5.serialization.SerializationCustomSerializer
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.framework.FrameworkUtil
import org.slf4j.LoggerFactory
import java.io.NotSerializableException
import java.lang.reflect.Type
import java.security.AccessControlContext
import java.security.AccessControlException
import java.security.BasicPermission
import java.security.PrivateKey

/**
 * Thrown when a [SerializationCustomSerializer] offers to serialize a type for which custom serialization is not permitted, because
 * it should be handled by standard serialisation methods (or not serialised at all) and there is no valid use case for
 * a custom method.
 */
class IllegalCustomSerializerException
private constructor(customSerializerQualifiedName: String?, clazz: Class<*>, cause: Exception?) : Exception(
    "Custom serializer $customSerializerQualifiedName " +
            "to serialize non-custom-serializable type $clazz",
    cause
) {
    constructor(customSerializer: AMQPSerializer<*>, clazz: Class<*>) :
            this(customSerializer::class.qualifiedName, clazz, null)

    constructor(customSerializer: SerializationCustomSerializer<*, *>, clazz: Class<*>, cause: Exception) :
            this(customSerializer::class.qualifiedName, clazz, cause)
}

/**
 * Thrown when more than one [SerializationCustomSerializer] offers to serialize the same type, which may indicate a malicious attempt
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
     * Register an internal custom serializer for any type that cannot be serialized or deserialized by the default
     * serializer that expects to find getters and a constructor with a parameter for each property.
     *
     * @param customSerializer a [CustomSerializer] that converts the target type to/from a proxy object
     */
    fun register(customSerializer: CustomSerializer<out Any>)

    /**
     * Register an internal custom serializer for any type that cannot be serialized or deserialized by the default
     * serializer that expects to find getters and a constructor with a parameter for each property.
     * This serializer is defined by an [InternalCustomSerializer][net.corda.serialization.InternalCustomSerializer]
     * inside an external Corda module.
     *
     * @param serializer an [InternalCustomSerializer] that converts the target type to/from a proxy object
     */
    fun register(serializer: InternalCustomSerializer<out Any>, factory: SerializerFactory)

    /**
     * Register a user defined custom serializer for any type that cannot be serialized or deserialized by the default
     * serializer that expects to find getters and a constructor with a parameter for each property.
     * This serializer is defined by a [SerializationCustomSerializer] inside a CorDapp.
     *
     * @param serializer a [SerializationCustomSerializer] that converts the target type to/from a proxy object
     */
    fun registerExternal(serializer: SerializationCustomSerializer<*, *>, factory: SerializerFactory)

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
        private val allowedFor: Set<Class<*>>,
        private val sandboxGroup: SandboxGroup
) : CustomSerializerRegistry {
    constructor(
        descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry,
        sandboxGroup: SandboxGroup
    ) : this(descriptorBasedSerializerRegistry, emptySet(), sandboxGroup)

    companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
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

    override fun register(customSerializer: CustomSerializer<out Any>) {
        logger.trace { "action=\"Registering custom serializer\", class=\"${customSerializer.type}\"" }
        registerCustomSerializer(customSerializer)
    }

    override fun register(serializer: InternalCustomSerializer<out Any>, factory: SerializerFactory) {
        register(when(serializer) {
            is InternalProxySerializer<out Any, out Any> -> CustomSerializer.Proxy(serializer, factory)
            is InternalDirectSerializer<out Any> -> CustomSerializer.Direct(serializer)
            else -> throw UnsupportedOperationException("Unknown custom serializer $serializer")
        })
    }

    override fun registerExternal(serializer: SerializationCustomSerializer<*, *>, factory: SerializerFactory) {
        val customSerializer = CorDappCustomSerializer(serializer, factory)

        val sm = System.getSecurityManager()
        if (sm != null) {
            val accessControlContext = sandboxGroup.metadata.keys.first().adapt(AccessControlContext::class.java)
            val customSerializerTargetBundle = FrameworkUtil.getBundle(customSerializer.type.asClass())
            try {
                sm.checkPermission(
                    CustomSerializerPermission(
                        customSerializerTargetBundle
                            ?.location
                            ?.substringBefore('/')
                            ?: NON_BUNDLE
                    ),
                    accessControlContext
                )
            } catch (ace: AccessControlException) {
                logger.warn("Illegal custom serializer detected for ${customSerializer.type}", ace)
                throw IllegalCustomSerializerException(serializer, serializer::class.java, ace)
            }
        }
        logger.trace { "action=\"Registering external serializer\", class=\"${customSerializer.type}\"" }
        registerCustomSerializer(customSerializer)
    }

    private fun checkActiveCache(type: Type) {
        if (customSerializersCache.isNotEmpty()) {
            val message = "Attempting to register custom serializer $type in an active cache. " +
                    "All serializers must be registered before the cache comes into use."
            logger.warn(message)
            throw AMQPNotSerializableException(type, message)
        }
    }

    private fun <T> registerCustomSerializer(customSerializer: T)
        where T: AMQPSerializer<Any>,
              T: SerializerFor {
        checkActiveCache(customSerializer.type)

        val descriptor = customSerializer.typeDescriptor.toString()
        val actualSerializer = descriptorBasedSerializerRegistry.getOrBuild(descriptor) {
            // We only register this serializer if one has not
            // already been registered for this type descriptor.
            customSerializers += customSerializer
            customSerializer
        }
        if (actualSerializer !== customSerializer) {
            logger.warn("Attempt to replace serializer for {}", descriptor)
        }
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

                    @Suppress("unchecked_cast")
                    customSerializer as? AMQPSerializer<Any>
                }
                else ->
                    // Make a subclass serializer for the subclass and return that...
                    @Suppress("unchecked_cast")
                    CustomSerializer.SubClass(clazz, customSerializer as CustomSerializer<Any>)
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

        return declaredSerializers.first()
    }

    private val Class<*>.isCustomSerializationForbidden: Boolean get() {
        val forbidden = true
        val allowed = false

        return when {
            isSubClassOf(PrivateKey::class.java) -> forbidden
            AMQPTypeIdentifiers.isPrimitive(this) -> forbidden
            isSubClassOf(CordaThrowable::class.java) -> allowed
            allowedFor.any { it.isAssignableFrom(this) } -> allowed
            isAnnotationPresent(CordaSerializable::class.java) && isAnnotationPresent(DoNotImplement::class.java) -> allowed
            isAnnotationPresent(CordaSerializable::class.java) && !isAnnotationPresent(DoNotImplement::class.java) -> forbidden
            else -> allowed
        }
    }
}

/**
 * [CustomSerializerPermission] is used to allow/ block external custom serializers registration. A register-able
 * external custom serializer should a serializer which is defined within a sandbox type X (X = FLOW or VERIFICATION or PERSISTENCE)
 * whose target type also exists in the same sandbox type X. Look for uses of this class in .policy files for example uses.
 *
 * @param [targetBundleLocation] is the external custom serializer target type.
 */
class CustomSerializerPermission(targetBundleLocation: String) : BasicPermission(targetBundleLocation) {
    init {
        // `BasicPermission` parsing of passed in `targetBundleLocation`, if it is to contain a wildcard ('*')
        // `BasicPermission` expects it to either be a single character ('*') or ending in (".*"). In our case, where we want
        // to pass in bundle locations, the passed in string would look like for e.g. "FLOW/*". But according to the above rule
        // this will not get parsed properly. So with using `BasicPermission` it means we should be using wildcard only
        // as a single character.
        // For cases like for e.g. "FLOW/*" we should only be passing in just "FLOW".
        if (targetBundleLocation.length > 1 && targetBundleLocation.endsWith('*')) {
            throw IllegalArgumentException("Permission name cannot end with *")
        }
    }

    companion object {
        // `BasicPermission` doesn't allow an empty name so using "NON_BUNDLE" if class is non bundled
        const val NON_BUNDLE = "NON_BUNDLE"
    }
}
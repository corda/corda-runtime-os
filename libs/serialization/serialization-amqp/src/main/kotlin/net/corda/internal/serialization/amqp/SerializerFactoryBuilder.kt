package net.corda.internal.serialization.amqp

import com.google.common.primitives.Primitives
import net.corda.internal.serialization.model.ClassTypeLoader
import net.corda.internal.serialization.model.ConfigurableLocalTypeModel
import net.corda.internal.serialization.model.FingerPrinter
import net.corda.internal.serialization.model.LocalTypeInformation
import net.corda.internal.serialization.model.RemoteTypeInformation
import net.corda.internal.serialization.model.TypeLoader
import net.corda.internal.serialization.model.TypeModellingFingerPrinter
import net.corda.v5.serialization.ClassWhitelist
import java.io.NotSerializableException
import java.lang.reflect.Method
import java.util.Collections.unmodifiableMap
import java.util.function.Function
import java.util.function.Predicate

object SerializerFactoryBuilder {
    private const val FRAMEWORK_UTIL_CLASS_NAME = "org.osgi.framework.FrameworkUtil"
    private const val BUNDLE_CONTEXT_CLASS_NAME = "org.osgi.framework.BundleContext"
    private const val BUNDLE_CLASS_NAME = "org.osgi.framework.Bundle"

    private val getBundle: Method?
    private val getBundleContext: Method?
    private val getBundles: Method?

    init {
        var getBundleMethod: Method?
        var getBundleContextMethod: Method?
        var getBundlesMethod: Method?

        @Suppress("TooGenericExceptionCaught")
        try {
            val frameworkUtilClass = Class.forName(FRAMEWORK_UTIL_CLASS_NAME, false, this::class.java.classLoader)
            val bundleContextClass = Class.forName(BUNDLE_CONTEXT_CLASS_NAME, false, frameworkUtilClass.classLoader)
            val bundleClass = Class.forName(BUNDLE_CLASS_NAME, false, frameworkUtilClass.classLoader)

            getBundleMethod = frameworkUtilClass.getMethod("getBundle", Class::class.java)
            checkReturnType(getBundleMethod, bundleClass)
            getBundleContextMethod = bundleClass.getMethod("getBundleContext")
            checkReturnType(getBundleContextMethod, bundleContextClass)
            getBundlesMethod = bundleContextClass.getMethod("getBundles")
            checkReturnType(getBundlesMethod, Array<Any>::class.java)
        } catch (e: Exception) {
            getBundleMethod = null
            getBundleContextMethod = null
            getBundlesMethod = null
        }

        getBundle = getBundleMethod
        getBundleContext = getBundleContextMethod
        getBundles = getBundlesMethod
    }

    private fun checkReturnType(method: Method, expectedType: Class<*>) {
        if (!expectedType.isAssignableFrom(method.returnType)) {
            throw IllegalArgumentException("Method $method should have return type ${expectedType.name}")
        }
    }

    // We may need to use serialization outside an OSGi framework,
    // e.g. the Network Bootstrapper.
    @Suppress("ThrowsCount")
    private fun getBundles(): List<Any> {
        val bundle = getBundle?.invoke(this::class.java)
                ?: throw NullPointerException("Class has no bundle.")
        val bundleContext = getBundleContext?.invoke(bundle)
                ?: throw NullPointerException("Bundle has no context.")
        val bundles = getBundles?.invoke(bundleContext)
                ?: throw NullPointerException("Context has no bundles.")
        @Suppress("unchecked_cast")
        return (bundles as Array<Any>).toList()
    }

    /**
     * The standard mapping of Java object types to Java primitive types.
     */
    @Suppress("unchecked_cast")
    private val javaPrimitiveTypes: Map<Class<*>, Class<*>> = unmodifiableMap(listOf(
        Boolean::class,
        Byte::class,
        Char::class,
        Double::class,
        Float::class,
        Int::class,
        Long::class,
        Short::class,
        Void::class
    ).associate {
        klazz -> klazz.javaObjectType to klazz.javaPrimitiveType
    }) as Map<Class<*>, Class<*>>

    @JvmStatic
    fun build(whitelist: ClassWhitelist): SerializerFactory {
        return makeFactory(
                whitelist,
                DefaultDescriptorBasedSerializerRegistry(),
                allowEvolution = true,
                overrideFingerPrinter = null,
                onlyCustomSerializers = false,
                mustPreserveDataWhenEvolving = false)
    }

    @Suppress("LongParameterList")
    @JvmStatic
    fun build(
            whitelist: ClassWhitelist,
            descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry =
                    DefaultDescriptorBasedSerializerRegistry(),
            allowEvolution: Boolean = true,
            overrideFingerPrinter: FingerPrinter? = null,
            onlyCustomSerializers: Boolean = false,
            mustPreserveDataWhenEvolving: Boolean = false): SerializerFactory {
        return makeFactory(
                whitelist,
                descriptorBasedSerializerRegistry,
                allowEvolution,
                overrideFingerPrinter,
                onlyCustomSerializers,
                mustPreserveDataWhenEvolving)
    }

    @Suppress("TooGenericExceptionCaught", "LongParameterList")
    private fun makeFactory(whitelist: ClassWhitelist,
                            descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry,
                            allowEvolution: Boolean,
                            overrideFingerPrinter: FingerPrinter?,
                            onlyCustomSerializers: Boolean,
                            mustPreserveDataWhenEvolving: Boolean): SerializerFactory {
        val customSerializerRegistry = CachingCustomSerializerRegistry(descriptorBasedSerializerRegistry)

        val typeModelConfiguration = WhitelistBasedTypeModelConfiguration(whitelist, customSerializerRegistry)
        val localTypeModel = ConfigurableLocalTypeModel(typeModelConfiguration)

        val fingerPrinter = overrideFingerPrinter ?: TypeModellingFingerPrinter(customSerializerRegistry)

        val localSerializerFactory = DefaultLocalSerializerFactory(
                whitelist,
                localTypeModel,
                fingerPrinter,
                descriptorBasedSerializerRegistry,
                Function { clazz -> AMQPPrimitiveSerializer(clazz) },
                Predicate { clazz -> clazz.isPrimitive || Primitives.unwrap(clazz).isPrimitive },
                customSerializerRegistry,
                onlyCustomSerializers)

        val typeLoader: TypeLoader = ClassTypeLoader()

        val evolutionSerializerFactory = if (allowEvolution) DefaultEvolutionSerializerFactory(
                localSerializerFactory,
                mustPreserveDataWhenEvolving,
                javaPrimitiveTypes,
                typeModelConfiguration.baseTypes
        ) else NoEvolutionSerializerFactory

        val remoteSerializerFactory = DefaultRemoteSerializerFactory(
                evolutionSerializerFactory,
                descriptorBasedSerializerRegistry,
                AMQPRemoteTypeModel(),
                localTypeModel,
                typeLoader,
                localSerializerFactory)

        return ComposedSerializerFactory(localSerializerFactory, remoteSerializerFactory, customSerializerRegistry)
    }

}

object NoEvolutionSerializerFactory : EvolutionSerializerFactory {
    override fun getEvolutionSerializer(remote: RemoteTypeInformation, local: LocalTypeInformation): AMQPSerializer<Any> {
        throw NotSerializableException("""
Evolution not permitted.

Remote:
${remote.prettyPrint(false)}

Local:
${local.prettyPrint(false)}
        """)
    }

    override val primitiveTypes: Map<Class<*>, Class<*>> = emptyMap()
}
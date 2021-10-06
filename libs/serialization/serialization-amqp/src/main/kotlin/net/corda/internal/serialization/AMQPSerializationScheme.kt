@file:JvmName("AMQPSerializationScheme")

package net.corda.internal.serialization

import net.corda.internal.serialization.amqp.AccessOrderLinkedHashMap
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.internal.serialization.amqp.amqpMagic
import net.corda.internal.serialization.amqp.custom.BigDecimalSerializer
import net.corda.internal.serialization.amqp.custom.BigIntegerSerializer
import net.corda.internal.serialization.amqp.custom.BitSetSerializer
import net.corda.internal.serialization.amqp.custom.CertPathSerializer
import net.corda.internal.serialization.amqp.custom.ClassSerializer
import net.corda.internal.serialization.amqp.custom.CurrencySerializer
import net.corda.internal.serialization.amqp.custom.DurationSerializer
import net.corda.internal.serialization.amqp.custom.EnumSetSerializer
import net.corda.internal.serialization.amqp.custom.InputStreamSerializer
import net.corda.internal.serialization.amqp.custom.InstantSerializer
import net.corda.internal.serialization.amqp.custom.LocalDateSerializer
import net.corda.internal.serialization.amqp.custom.LocalDateTimeSerializer
import net.corda.internal.serialization.amqp.custom.LocalTimeSerializer
import net.corda.internal.serialization.amqp.custom.MonthDaySerializer
import net.corda.internal.serialization.amqp.custom.OffsetDateTimeSerializer
import net.corda.internal.serialization.amqp.custom.OffsetTimeSerializer
import net.corda.internal.serialization.amqp.custom.OpaqueBytesSubSequenceSerializer
import net.corda.internal.serialization.amqp.custom.OptionalSerializer
import net.corda.internal.serialization.amqp.custom.PeriodSerializer
import net.corda.internal.serialization.amqp.custom.StackTraceElementSerializer
import net.corda.internal.serialization.amqp.custom.StringBufferSerializer
import net.corda.internal.serialization.amqp.custom.ThrowableSerializer
import net.corda.internal.serialization.amqp.custom.X500PrincipalSerializer
import net.corda.internal.serialization.amqp.custom.X509CRLSerializer
import net.corda.internal.serialization.amqp.custom.X509CertificateSerializer
import net.corda.internal.serialization.amqp.custom.YearMonthSerializer
import net.corda.internal.serialization.amqp.custom.YearSerializer
import net.corda.internal.serialization.amqp.custom.ZoneIdSerializer
import net.corda.internal.serialization.amqp.custom.ZonedDateTimeSerializer
import net.corda.internal.serialization.custom.PrivateKeySerializer
import net.corda.internal.serialization.custom.PublicKeySerializer
import net.corda.sandbox.SandboxGroup
import net.corda.utilities.toSynchronised
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.ByteSequence
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.serialization.ClassWhitelist
import net.corda.v5.serialization.SerializationContext
import net.corda.v5.serialization.SerializationCustomSerializer
import net.corda.v5.serialization.SerializationWhitelist
import net.corda.v5.serialization.SerializedBytes
import java.security.PublicKey
import java.util.Collections

val AMQP_ENABLED get() = effectiveSerializationEnv.p2pContext.preferredSerializationVersion == amqpMagic

data class SerializationFactoryCacheKey(val classWhitelist: ClassWhitelist,
                                        val sandboxGroup: SandboxGroup?,
                                        val preventDataLoss: Boolean,
                                        val customSerializers: Set<SerializationCustomSerializer<*, *>>?)

fun SerializerFactory.addToWhitelist(types: Collection<Class<*>>) {
    require(types.toSet().size == types.size) {
        val duplicates = types.toMutableList()
        types.toSet().forEach { duplicates -= it }
        "Cannot add duplicate classes to the whitelist ($duplicates)."
    }
    val mutableClassWhitelist = this.whitelist as? MutableClassWhitelist
        ?: throw CordaRuntimeException("whitelist is not an instance of MutableClassWhitelist, cannot whitelist types")
    for (type in types) {
        mutableClassWhitelist.add(type)
    }
}

data class InternalCustomSerializerRegistrationData(
    val customSerializer: SerializationCustomSerializer<*, *>,
    val withInheritance: Boolean,
    val revealSubclassesInSchema: Boolean = false
)

abstract class AbstractAMQPSerializationScheme private constructor(
        private val cordappCustomSerializers: Set<SerializationCustomSerializer<*, *>>,
        private val cordappSerializationWhitelists: Set<SerializationWhitelist>,
        maybeNotConcurrentSerializerFactoriesForContexts: MutableMap<SerializationFactoryCacheKey, SerializerFactory>,
        cipherSchemeMetadata: CipherSchemeMetadata,
        val sff: SerializerFactoryFactory = createSerializerFactoryFactory(),
        internalCustomSerializerFactories: Set<(factory: SerializerFactory) -> InternalCustomSerializerRegistrationData> = emptySet()
) : SerializationScheme {
    constructor(cipherSchemeMetadata: CipherSchemeMetadata) : this(
        emptySet<SerializationCustomSerializer<*, *>>(),
        emptySet<SerializationWhitelist>(),
        AccessOrderLinkedHashMap<SerializationFactoryCacheKey, SerializerFactory>(128).toSynchronised(),
        cipherSchemeMetadata
    )

    private val _internalCustomSerializerFactories: MutableSet<(factory: SerializerFactory) -> InternalCustomSerializerRegistrationData> =
        mutableSetOf()
    private val internalCustomSerializerFactories: Set<(factory: SerializerFactory) -> InternalCustomSerializerRegistrationData> =
        _internalCustomSerializerFactories

    init {
        internalCustomSerializerFactories.forEach {
            registerInternalCustomSerializerFactory(it)
        }
    }

    private fun registerInternalCustomSerializerFactory(factory: (factory: SerializerFactory) -> InternalCustomSerializerRegistrationData) {
        _internalCustomSerializerFactories += factory
    }

    @VisibleForTesting
    fun getRegisteredCustomSerializers() = cordappCustomSerializers

    // This is a bit gross but a broader check for ConcurrentMap is not allowed inside DJVM.
    private val serializerFactoriesForContexts: MutableMap<SerializationFactoryCacheKey, SerializerFactory> =
            if (maybeNotConcurrentSerializerFactoriesForContexts is
                            AccessOrderLinkedHashMap<SerializationFactoryCacheKey, SerializerFactory>) {
                Collections.synchronizedMap(maybeNotConcurrentSerializerFactoriesForContexts)
            } else {
                maybeNotConcurrentSerializerFactoriesForContexts
            }

    companion object {
        private val serializationWhitelists: List<SerializationWhitelist> by lazy { listOf(DefaultWhitelist) }
    }

    private fun registerCustomSerializers(context: SerializationContext, factory: SerializerFactory) {
        factory.register(publicKeySerializer, true, factory = factory)
        registerCustomSerializers(factory)

        internalCustomSerializerFactories.forEach{
            val registrationData = it(factory)
            factory.register(
                registrationData.customSerializer,
                registrationData.withInheritance,
                registrationData.revealSubclassesInSchema,
                factory
            )
        }

        val serializersToRegister = context.customSerializers ?: cordappCustomSerializers
        serializersToRegister.forEach { customSerializer ->
            factory.registerExternal(customSerializer, factory)
        }
    }

    private fun registerCustomWhitelists(factory: SerializerFactory) {
        serializationWhitelists.forEach {
            factory.addToWhitelist(it.whitelist)
        }
        cordappSerializationWhitelists.forEach {
            factory.addToWhitelist(it.whitelist)
        }
    }

    // Not used as a simple direct import to facilitate testing
    open val publicKeySerializer: SerializationCustomSerializer<PublicKey, ByteArray> = PublicKeySerializer(cipherSchemeMetadata)

    fun getSerializerFactory(context: SerializationContext): SerializerFactory {
        val sandboxGroup = context.sandboxGroup as? SandboxGroup
        val key = SerializationFactoryCacheKey(context.whitelist, sandboxGroup, context.preventDataLoss, context.customSerializers)
        // ConcurrentHashMap.get() is lock free, but computeIfAbsent is not, even if the key is in the map already.
        return serializerFactoriesForContexts[key] ?: serializerFactoriesForContexts.computeIfAbsent(key) {
            sff.make(context).also {
                registerCustomSerializers(context, it)
                registerCustomWhitelists(it)
            }
        }
    }

    override fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): T {
        val serializerFactory = getSerializerFactory(context)
        return DeserializationInput(serializerFactory).deserialize(byteSequence, clazz, context)
    }

    override fun <T : Any> serialize(obj: T, context: SerializationContext): SerializedBytes<T> {
        val serializerFactory = getSerializerFactory(context)
        return SerializationOutput(serializerFactory).serialize(obj, context)
    }

    protected fun canDeserializeVersion(magic: CordaSerializationMagic) = magic == amqpMagic
}

fun registerCustomSerializers(factory: SerializerFactory) {
    with(factory) {
        register(ThrowableSerializer(this), true, true, factory = this)
        register(StackTraceElementSerializer(), true, factory = this)
        register(BigDecimalSerializer, false, factory = this)
        register(BigIntegerSerializer, false, factory = this)
        register(CurrencySerializer, false, factory = this)
        register(OpaqueBytesSubSequenceSerializer(), true, factory = this)
        register(InstantSerializer(), true, factory = this)
        register(DurationSerializer(), true, factory = this)
        register(LocalDateSerializer(), true, factory = this)
        register(LocalDateTimeSerializer(), true, factory = this)
        register(LocalTimeSerializer(), true, factory = this)
        register(ZonedDateTimeSerializer(), true, factory = this)
        register(ZoneIdSerializer(), true, true, factory = this)
        register(OffsetTimeSerializer(), true, factory = this)
        register(OffsetDateTimeSerializer(), true, factory = this)
        register(OptionalSerializer(), true, factory = this)
        register(YearSerializer(), true, factory = this)
        register(YearMonthSerializer(), true, factory = this)
        register(MonthDaySerializer(), true, factory = this)
        register(PeriodSerializer(), true, factory = this)
        register(ClassSerializer(), true, factory = this)
        register(X509CertificateSerializer, true, factory = this)
        register(X509CRLSerializer, true, factory = this)
        register(CertPathSerializer(), true, factory = this)
        register(StringBufferSerializer, false, factory = this)
        register(InputStreamSerializer, true, factory = this)
        register(BitSetSerializer(), true, factory = this)
        register(EnumSetSerializer(), true, factory = this)
        register(X500PrincipalSerializer(), true, factory = this)
        register(PrivateKeySerializer, true, factory = this)
    }
}


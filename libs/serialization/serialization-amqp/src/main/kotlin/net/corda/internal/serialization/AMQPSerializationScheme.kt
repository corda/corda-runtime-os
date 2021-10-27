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
import net.corda.sandbox.SandboxGroup
import net.corda.utilities.toSynchronised
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.ByteSequence
import net.corda.v5.serialization.ClassWhitelist
import net.corda.v5.serialization.SerializationContext
import net.corda.v5.serialization.SerializationCustomSerializer
import net.corda.v5.serialization.SerializationWhitelist
import net.corda.v5.serialization.SerializedBytes
import java.util.Collections

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

abstract class AbstractAMQPSerializationScheme private constructor(
        private val cordappCustomSerializers: Set<SerializationCustomSerializer<*, *>>,
        private val cordappSerializationWhitelists: Set<SerializationWhitelist>,
        maybeNotConcurrentSerializerFactoriesForContexts: MutableMap<SerializationFactoryCacheKey, SerializerFactory>,
        val sff: SerializerFactoryFactory = createSerializerFactoryFactory()
) : SerializationScheme {
    constructor() : this(
        emptySet<SerializationCustomSerializer<*, *>>(),
        emptySet<SerializationWhitelist>(),
        AccessOrderLinkedHashMap<SerializationFactoryCacheKey, SerializerFactory>(128).toSynchronised()
    )

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
        registerCustomSerializers(factory)

        val serializersToRegister = context.customSerializers ?: cordappCustomSerializers
        serializersToRegister.forEach { customSerializer ->
            factory.registerExternal(customSerializer)
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
        register(ThrowableSerializer(this), true)
        register(StackTraceElementSerializer(), true)
        register(BigDecimalSerializer, false)
        register(BigIntegerSerializer, false)
        register(CurrencySerializer, false)
        register(OpaqueBytesSubSequenceSerializer(), true)
        register(InstantSerializer(), true)
        register(DurationSerializer(), true)
        register(LocalDateSerializer(), true)
        register(LocalDateTimeSerializer(), true)
        register(LocalTimeSerializer(), true)
        register(ZonedDateTimeSerializer(), true)
        register(ZoneIdSerializer(), true)
        register(OffsetTimeSerializer(), true)
        register(OffsetDateTimeSerializer(), true)
        register(OptionalSerializer(), true)
        register(YearSerializer(), true)
        register(YearMonthSerializer(), true)
        register(MonthDaySerializer(), true)
        register(PeriodSerializer(), true)
        register(ClassSerializer(), true)
        register(X509CertificateSerializer, true)
        register(X509CRLSerializer, true)
        register(CertPathSerializer(), true)
        register(StringBufferSerializer, false)
        register(InputStreamSerializer, true)
        register(BitSetSerializer(), true)
        register(EnumSetSerializer(), true)
        register(X500PrincipalSerializer(), true)
    }
}


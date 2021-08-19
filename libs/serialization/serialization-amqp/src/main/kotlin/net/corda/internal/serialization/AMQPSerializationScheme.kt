@file:JvmName("AMQPSerializationScheme")

package net.corda.internal.serialization

import net.corda.internal.serialization.amqp.AccessOrderLinkedHashMap
import net.corda.internal.serialization.amqp.CorDappCustomSerializer
import net.corda.internal.serialization.amqp.CustomSerializer
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.internal.serialization.amqp.amqpMagic
import net.corda.internal.serialization.custom.PrivateKeySerializer
import net.corda.internal.serialization.custom.PublicKeySerializer
import net.corda.sandbox.SandboxGroup
import net.corda.utilities.toSynchronised
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.uncheckedCast
import net.corda.v5.base.types.ByteSequence
import net.corda.v5.serialization.ClassWhitelist
import net.corda.v5.serialization.ContextPropertyKeys
import net.corda.v5.serialization.SerializationContext
import net.corda.v5.serialization.SerializationCustomSerializer
import net.corda.v5.serialization.SerializationWhitelist
import net.corda.v5.serialization.SerializedBytes
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
    for (type in types) {
        (this.whitelist as? MutableClassWhitelist)?.add(type)
    }
}

abstract class AbstractAMQPSerializationScheme(
        private val cordappCustomSerializers: Set<SerializationCustomSerializer<*, *>>,
        private val cordappSerializationWhitelists: Set<SerializationWhitelist>,
        maybeNotConcurrentSerializerFactoriesForContexts: MutableMap<SerializationFactoryCacheKey, SerializerFactory>,
        val sff: SerializerFactoryFactory = createSerializerFactoryFactory(),
        internalCustomSerializerFactories: Set<(factory: SerializerFactory) -> CustomSerializer<out Any>> = emptySet()
) : SerializationScheme {
    constructor() : this(
        emptySet<SerializationCustomSerializer<*, *>>(),
        emptySet<SerializationWhitelist>(),
        AccessOrderLinkedHashMap<SerializationFactoryCacheKey, SerializerFactory>(128).toSynchronised()
    )

    private val _internalCustomSerializerFactories: MutableSet<(factory: SerializerFactory) -> CustomSerializer<out Any>> = mutableSetOf()
    val internalCustomSerializerFactories: Set<(factory: SerializerFactory) -> CustomSerializer<out Any>> = _internalCustomSerializerFactories

    init {
        internalCustomSerializerFactories.forEach {
            registerInternalCustomSerializerFactory(it)
        }
    }

    fun registerInternalCustomSerializerFactory(factory: (factory: SerializerFactory) -> CustomSerializer<out Any>) {
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
        factory.register(publicKeySerializer)
        registerCustomSerializers(factory)

        internalCustomSerializerFactories.forEach{
            factory.register(it(factory))
        }

        val serializersToRegister = context.customSerializers ?: cordappCustomSerializers
        serializersToRegister.forEach { customSerializer ->
            factory.registerExternal(CorDappCustomSerializer(customSerializer, factory))
        }

        context.properties[ContextPropertyKeys.SERIALIZERS]?.apply {
            uncheckedCast<Any, List<CustomSerializer<out Any>>>(this).forEach {
                factory.register(it)
            }
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
    open val publicKeySerializer: CustomSerializer<*> = PublicKeySerializer

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
        register(net.corda.internal.serialization.amqp.custom.ThrowableSerializer(this))
        register(net.corda.internal.serialization.amqp.custom.BigDecimalSerializer)
        register(net.corda.internal.serialization.amqp.custom.BigIntegerSerializer)
        register(net.corda.internal.serialization.amqp.custom.CurrencySerializer)
        register(net.corda.internal.serialization.amqp.custom.OpaqueBytesSubSequenceSerializer(this))
        register(net.corda.internal.serialization.amqp.custom.InstantSerializer(this))
        register(net.corda.internal.serialization.amqp.custom.DurationSerializer(this))
        register(net.corda.internal.serialization.amqp.custom.LocalDateSerializer(this))
        register(net.corda.internal.serialization.amqp.custom.LocalDateTimeSerializer(this))
        register(net.corda.internal.serialization.amqp.custom.LocalTimeSerializer(this))
        register(net.corda.internal.serialization.amqp.custom.ZonedDateTimeSerializer(this))
        register(net.corda.internal.serialization.amqp.custom.ZoneIdSerializer(this))
        register(net.corda.internal.serialization.amqp.custom.OffsetTimeSerializer(this))
        register(net.corda.internal.serialization.amqp.custom.OffsetDateTimeSerializer(this))
        register(net.corda.internal.serialization.amqp.custom.OptionalSerializer(this))
        register(net.corda.internal.serialization.amqp.custom.YearSerializer(this))
        register(net.corda.internal.serialization.amqp.custom.YearMonthSerializer(this))
        register(net.corda.internal.serialization.amqp.custom.MonthDaySerializer(this))
        register(net.corda.internal.serialization.amqp.custom.PeriodSerializer(this))
        register(net.corda.internal.serialization.amqp.custom.ClassSerializer(this))
        register(net.corda.internal.serialization.amqp.custom.X509CertificateSerializer)
        register(net.corda.internal.serialization.amqp.custom.X509CRLSerializer)
        register(net.corda.internal.serialization.amqp.custom.CertPathSerializer(this))
        register(net.corda.internal.serialization.amqp.custom.StringBufferSerializer)
        register(net.corda.internal.serialization.amqp.custom.InputStreamSerializer)
        register(net.corda.internal.serialization.amqp.custom.BitSetSerializer(this))
        register(net.corda.internal.serialization.amqp.custom.EnumSetSerializer(this))
        register(net.corda.internal.serialization.amqp.custom.X500PrincipalSerializer(this))
        register(PrivateKeySerializer)
    }
}


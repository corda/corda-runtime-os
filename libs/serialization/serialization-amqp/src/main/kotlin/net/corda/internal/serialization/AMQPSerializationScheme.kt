@file:JvmName("AMQPSerializationScheme")
package net.corda.internal.serialization

import net.corda.internal.serialization.amqp.AccessOrderLinkedHashMap
import net.corda.internal.serialization.amqp.ClassloadingContext
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.internal.serialization.amqp.amqpMagic
import net.corda.internal.serialization.amqp.currentClassloadingContext
import net.corda.internal.serialization.amqp.custom.*
import net.corda.serialization.SerializationContext
import net.corda.utilities.VisibleForTesting
import net.corda.utilities.toSynchronised
import net.corda.v5.base.types.ByteSequence
import net.corda.v5.serialization.SerializationCustomSerializer
import net.corda.v5.serialization.SerializedBytes
import java.util.Collections

data class SerializationFactoryCacheKey(val classloadingContext: ClassloadingContext?,
                                        val preventDataLoss: Boolean,
                                        val customSerializers: Set<SerializationCustomSerializer<*, *>>?)

abstract class AbstractAMQPSerializationScheme private constructor(
        private val cordappCustomSerializers: Set<SerializationCustomSerializer<*, *>>,
        maybeNotConcurrentSerializerFactoriesForContexts: MutableMap<SerializationFactoryCacheKey, SerializerFactory>,
        val sff: SerializerFactoryFactory = createSerializerFactoryFactory()
) : SerializationScheme {
    constructor() : this(
        emptySet<SerializationCustomSerializer<*, *>>(),
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

    private fun registerCustomSerializers(context: SerializationContext, factory: SerializerFactory) {
        registerCustomSerializers(factory)

        val serializersToRegister = context.customSerializers ?: cordappCustomSerializers
        serializersToRegister.forEach { customSerializer ->
            factory.registerExternal(customSerializer, factory)
        }
    }

    fun getSerializerFactory(context: SerializationContext): SerializerFactory {
        val classloadingContext = context.currentClassloadingContext()
        val key = SerializationFactoryCacheKey(classloadingContext, context.preventDataLoss, context.customSerializers)
        // ConcurrentHashMap.get() is lock free, but computeIfAbsent is not, even if the key is in the map already.
        return serializerFactoriesForContexts[key] ?: serializerFactoriesForContexts.computeIfAbsent(key) {
            sff.make(context).also {
                registerCustomSerializers(context, it)
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
        register(ThrowableSerializer(this), this)
        register(StackTraceElementSerializer(), this)
        register(BigDecimalSerializer(), this)
        register(BigIntegerSerializer(), this)
        register(CurrencySerializer(), this)
        register(OpaqueBytesSubSequenceSerializer(), this)
        register(InstantSerializer(), this)
        register(DurationSerializer(), this)
        register(LocalDateSerializer(), this)
        register(LocalDateTimeSerializer(), this)
        register(LocalTimeSerializer(), this)
        register(ZonedDateTimeSerializer(), this)
        register(ZoneIdSerializer(), this)
        register(OffsetTimeSerializer(), this)
        register(OffsetDateTimeSerializer(), this)
        register(OptionalSerializer(), this)
        register(YearSerializer(), this)
        register(YearMonthSerializer(), this)
        register(MonthDaySerializer(), this)
        register(PeriodSerializer(), this)
        register(ClassSerializer(), this)
        register(X509CertificateSerializer(), this)
        register(X509CRLSerializer(), this)
        register(CertPathSerializer(), this)
        register(StringBufferSerializer(), this)
        register(InputStreamSerializer(), this)
        register(BitSetSerializer(), this)
        register(EnumSetSerializer(), this)
        register(X500PrincipalSerializer(), this)
        register(DayOfWeekSerializer(), this)
        register(MonthSerializer(), this)
        register(PairSerializer(), this)
        register(UnitSerializer(), this)
    }
}

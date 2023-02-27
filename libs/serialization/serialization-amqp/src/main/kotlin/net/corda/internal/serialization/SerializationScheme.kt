package net.corda.internal.serialization

import net.corda.internal.serialization.amqp.amqpMagic
import net.corda.serialization.EncodingAllowList
import net.corda.serialization.ObjectWithCompatibleContext
import net.corda.serialization.SerializationContext
import net.corda.serialization.SerializationEncoding
import net.corda.serialization.SerializationFactory
import net.corda.serialization.SerializationMagic
import net.corda.utilities.VisibleForTesting
import net.corda.v5.base.types.ByteSequence
import net.corda.v5.serialization.SerializationCustomSerializer
import net.corda.v5.serialization.SerializedBytes
import org.slf4j.LoggerFactory
import java.io.NotSerializableException
import java.nio.ByteBuffer
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

object NullEncodingAllowList : EncodingAllowList {
    override fun acceptEncoding(encoding: SerializationEncoding) = false
}

object SnappyEncodingAllowList : EncodingAllowList {
    override fun acceptEncoding(encoding: SerializationEncoding): Boolean {
        return encoding == CordaSerializationEncoding.SNAPPY
    }
}

data class SerializationContextImpl @JvmOverloads constructor(
    override val preferredSerializationVersion: SerializationMagic,
    override val properties: Map<Any, Any>,
    override val objectReferencesEnabled: Boolean,
    override val useCase: SerializationContext.UseCase,
    override val encoding: SerializationEncoding?,
    override val encodingAllowList: EncodingAllowList = SnappyEncodingAllowList,
    override val preventDataLoss: Boolean = false,
    override val customSerializers: Set<SerializationCustomSerializer<*, *>>? = null,
    override val sandboxGroup: Any? = null
) : SerializationContext {

    override fun withClassLoader(classLoader: ClassLoader): SerializationContext {
        TODO("Not yet implemented")
    }

    override fun withSandboxGroup(sandboxGroup: Any): SerializationContext = copy(sandboxGroup = sandboxGroup)

    override fun withProperty(property: Any, value: Any): SerializationContext {
        return copy(properties = properties + (property to value))
    }

    override fun withoutReferences(): SerializationContext {
        return copy(objectReferencesEnabled = false)
    }

    override fun withPreventDataLoss(): SerializationContext = copy(preventDataLoss = true)

    override fun withCustomSerializers(serializers: Set<SerializationCustomSerializer<*, *>>): SerializationContextImpl {
        return copy(customSerializers = customSerializers?.union(serializers) ?: serializers)
    }

    override fun withPreferredSerializationVersion(magic: SerializationMagic) = copy(preferredSerializationVersion = magic)
    override fun withEncoding(encoding: SerializationEncoding?) = copy(encoding = encoding)
    override fun withEncodingAllowList(encodingAllowList: EncodingAllowList) = copy(encodingAllowList = encodingAllowList)
}

open class SerializationFactoryImpl(
    // TODO: This is read-mostly. Probably a faster implementation to be found.
    private val schemes: MutableMap<Pair<CordaSerializationMagic, SerializationContext.UseCase>, SerializationScheme>
) : SerializationFactory {
    constructor() : this(ConcurrentHashMap())

    companion object {
        val magicSize = amqpMagic.size
    }

    @VisibleForTesting
    fun getRegisteredSchemes() = registeredSchemes

    private val creator: List<StackTraceElement> = Exception().stackTrace.asList()

    private val registeredSchemes: MutableCollection<SerializationScheme> = Collections.synchronizedCollection(mutableListOf())

    private val logger = LoggerFactory.getLogger(javaClass)

    private fun ByteBuffer.copyBytes(): ByteArray = ByteArray(remaining()).also { get(it) }

    private fun schemeFor(byteSequence: ByteSequence, target: SerializationContext.UseCase): Pair<SerializationScheme, CordaSerializationMagic> {
        // truncate sequence to at most magicSize, and make sure it's a copy to avoid holding onto large ByteArrays
        val magic = CordaSerializationMagic(byteSequence.slice(0, magicSize).copyBytes())
        val lookupKey = magic to target
        // ConcurrentHashMap.get() is lock free, but computeIfAbsent is not, even if the key is in the map already.
        return (
            schemes[lookupKey] ?: schemes.computeIfAbsent(lookupKey) {
                registeredSchemes.firstOrNull {
                    it.canDeserializeVersion(magic, target)
                } ?: run {
                    logger.warn(
                        "Cannot find serialization scheme for: [$lookupKey, " +
                                "${if (magic == amqpMagic) "AMQP" else "UNKNOWN MAGIC"}] registeredSchemes are: $registeredSchemes"
                    )
                    throw UnsupportedOperationException("Serialization scheme $lookupKey not supported.")
                }
            }
        ) to magic
    }

    @Throws(NotSerializableException::class)
    override fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): T {
        return schemeFor(byteSequence, context.useCase).first.deserialize(byteSequence, clazz, context)
    }

    @Throws(NotSerializableException::class)
    override fun <T : Any> deserializeWithCompatibleContext(
        byteSequence: ByteSequence,
        clazz: Class<T>,
        context: SerializationContext
    ): ObjectWithCompatibleContext<T> {
        val (scheme, magic) = schemeFor(byteSequence, context.useCase)
        val deserializedObject = scheme.deserialize(byteSequence, clazz, context)
        return ObjectWithCompatibleContext(deserializedObject, context.withPreferredSerializationVersion(magic))
    }

    override fun <T : Any> serialize(obj: T, context: SerializationContext): SerializedBytes<T> {
        return schemeFor(context.preferredSerializationVersion, context.useCase).first.serialize(obj, context)
    }

    fun registerScheme(scheme: SerializationScheme) {
        check(schemes.isEmpty()) { "All serialization schemes must be registered before any scheme is used." }
        registeredSchemes += scheme
    }

    override fun toString(): String {
        return "${this.javaClass.name} registeredSchemes=$registeredSchemes ${creator.joinToString("\n")}"
    }

    override fun equals(other: Any?): Boolean {
        return other is SerializationFactoryImpl && other.registeredSchemes == this.registeredSchemes
    }

    override fun hashCode(): Int = registeredSchemes.hashCode()
}

interface SerializationScheme {
    fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase): Boolean
    @Throws(NotSerializableException::class)
    fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): T

    @Throws(NotSerializableException::class)
    fun <T : Any> serialize(obj: T, context: SerializationContext): SerializedBytes<T>
}

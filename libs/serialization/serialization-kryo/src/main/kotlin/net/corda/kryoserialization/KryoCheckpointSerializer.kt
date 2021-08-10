package net.corda.kryoserialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.pool.KryoPool
import com.esotericsoftware.kryo.serializers.ClosureSerializer
import net.corda.kryoserialization.serializers.ClassSerializer
import net.corda.kryoserialization.serializers.CordaClosureSerializer
import net.corda.serialization.CheckpointInternalCustomSerializer
import net.corda.serialization.CheckpointSerializationContext
import net.corda.serialization.CheckpointSerializer
import net.corda.v5.base.types.ByteSequence
import net.corda.v5.base.util.loggerFor
import net.corda.v5.base.util.uncheckedCast
import net.corda.v5.crypto.BasicHashingService
import net.corda.v5.serialization.CheckpointCustomSerializer
import net.corda.v5.serialization.ClassWhitelist
import net.corda.v5.serialization.SerializationWhitelist
import java.util.concurrent.ConcurrentHashMap

val kryoMagic = CordaSerializationMagic("corda".toByteArray() + byteArrayOf(0, 0))

private object AutoCloseableSerialisationDetector : Serializer<AutoCloseable>() {
    override fun write(kryo: Kryo, output: Output, closeable: AutoCloseable) {
        val message = "${closeable.javaClass.name}, which is a closeable resource, has been detected during flow " +
                "checkpointing. Restoring such resources across node restarts is not supported. Make sure code " +
                "accessing it is confined to a private method or the reference is nulled out."
        throw UnsupportedOperationException(message)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<AutoCloseable>) = throw IllegalStateException(
        "Should " +
                "not reach here!"
    )
}

class KryoCheckpointSerializerBuilder(
    private val kryoFromQuasar: () -> Kryo,
    private val hashingService: BasicHashingService
) {
    private val noReferenceWithin = mutableListOf<Class<*>>()
    private val serializers = mutableMapOf<Class<*>, CheckpointInternalCustomSerializer<*>>()

    fun addSerializer(clazz: Class<*>, serializer: CheckpointInternalCustomSerializer<*>) {
        serializers[clazz] = serializer
    }

    fun addSerializerForClasses(classes: List<Class<*>>, serializer: CheckpointInternalCustomSerializer<*>) {
        for (clazz in classes) {
            addSerializer(clazz, serializer)
        }
    }

    fun addNoReferencesWithin(clazz: Class<*>) {
        noReferenceWithin += clazz
    }

    fun build(): KryoCheckpointSerializer {
        return KryoCheckpointSerializer(
            kryoFromQuasar,
            serializers,
            noReferenceWithin,
            DefaultWhitelist,
            hashingService
        )
    }
}

@Suppress("LongParameterList")
class KryoCheckpointSerializer(
    private val kryoFromQuasar: () -> Kryo,
    private val serializers: Map<Class<*>, CheckpointInternalCustomSerializer<*>>,
    private val noReferenceWithin: List<Class<*>>,
    private val defaultWhiteList: SerializationWhitelist,
    private val hashingService: BasicHashingService
) : CheckpointSerializer {
    private val kryoPoolsForContexts = ConcurrentHashMap<KryoPoolKey, KryoPool>()

    /**
     * As not all the objects the [CheckpointSerializationContext] which are used in [Kryo], the ones which are
     * should be added to [KryoPoolKey] so that [getPool] extracts a correctly configured [KryoPool].
     */
    private data class KryoPoolKey(
        val classWhiteList: ClassWhitelist,
        val classLoader: ClassLoader,
        val checkpointCustomSerializers: Iterable<CheckpointCustomSerializer<*, *>>,
        val classInfoService: Any?,
        val sandboxGroup: Any?
    ) {
        constructor(
            context: CheckpointSerializationContext
        ) : this(
            context.whitelist,
            context.deserializationClassLoader,
            context.checkpointCustomSerializers,
            context.classInfoService,
            context.sandboxGroup
        )
    }

    private fun getPool(context: CheckpointSerializationContext): KryoPool {
        return kryoPoolsForContexts.computeIfAbsent(KryoPoolKey(context)) {
            KryoPool.Builder {
                val kryoFromQuasar = kryoFromQuasar()
                val classResolver = CordaClassResolver(context, hashingService).apply { setKryo(kryoFromQuasar) }
                // TODO The ClassResolver can only be set in the Kryo constructor and Quasar doesn't provide us with a way of doing that
                val field = Kryo::class.java.getDeclaredField("classResolver").apply { isAccessible = true }
                kryoFromQuasar.apply {
                    field.set(this, classResolver)
                    // don't allow overriding the public key serializer for checkpointing
                    DefaultKryoCustomizer(defaultWhiteList).customize(this)
                    addDefaultSerializer(AutoCloseable::class.java, AutoCloseableSerialisationDetector)
                    register(ClosureSerializer.Closure::class.java, CordaClosureSerializer)
                    register(
                        Class::class.java,
                        ClassSerializer(context.classInfoService, context.sandboxGroup, hashingService)
                    )
                    classLoader = context.deserializationClassLoader

                    //Add external serializers
                    for (serializer in serializers) {
                        register(serializer.key, KryoCheckpointSerializerAdapter(serializer.value).adapt())
                    }
                    for (noRef in noReferenceWithin) {
                        register(noRef, NoReferencesSerializer(getSerializer(noRef)))
                    }
                    // Add custom serializers
                    val customSerializers = buildCustomSerializerAdaptors(context)
                    warnAboutDuplicateSerializers(customSerializers)
                    val classToSerializer =
                        mapInputClassToCustomSerializer(context.deserializationClassLoader, customSerializers)
                    addDefaultCustomSerializers(this, classToSerializer)
                }
            }.build()

        }
    }

    /**
     * Returns a sorted list of CustomSerializerCheckpointAdaptor based on the custom serializers inside context.
     *
     * The adaptors are sorted by serializerName which maps to javaClass.name for the serializer class
     */
    private fun buildCustomSerializerAdaptors(context: CheckpointSerializationContext) =
        context.checkpointCustomSerializers.map { CustomSerializerCheckpointAdaptor(it) }.sortedBy { it.serializerName }

    /**
     * Returns a list of pairs where the first element is the input class of the custom serializer and the second element is the
     * custom serializer.
     */
    private fun mapInputClassToCustomSerializer(
        classLoader: ClassLoader,
        customSerializers: Iterable<CustomSerializerCheckpointAdaptor<*, *>>
    ) =
        customSerializers.map { getInputClassForCustomSerializer(classLoader, it) to it }

    /**
     * Returns the Class object for the serializers input type.
     */
    private fun getInputClassForCustomSerializer(
        classLoader: ClassLoader,
        customSerializer: CustomSerializerCheckpointAdaptor<*, *>
    ): Class<*> {
        val typeNameWithoutGenerics = customSerializer.cordappType.typeName.substringBefore('<')
        return Class.forName(typeNameWithoutGenerics, false, classLoader)
    }

    /**
     * Emit a warning if two or more custom serializers are found for the same input type.
     */
    private fun warnAboutDuplicateSerializers(customSerializers: Iterable<CustomSerializerCheckpointAdaptor<*, *>>) =
        customSerializers
            .groupBy({ it.cordappType }, { it.serializerName })
            .filter { (_, serializerNames) -> serializerNames.distinct().size > 1 }
            .forEach { (inputType, serializerNames) ->
                loggerFor<KryoCheckpointSerializer>().warn(
                    "Duplicate custom checkpoint serializer for type $inputType. Serializers: ${
                        serializerNames.joinToString(
                            ", "
                        )
                    }"
                )
            }

    /**
     * Register all custom serializers as default, this class + subclass, registrations.
     *
     * Serializers registered before this will take priority. This needs to run after registrations we want to keep otherwise it may
     * replace them.
     */
    private fun addDefaultCustomSerializers(
        kryo: Kryo,
        classToSerializer: Iterable<Pair<Class<*>, CustomSerializerCheckpointAdaptor<*, *>>>
    ) = classToSerializer
        .forEach { (clazz, customSerializer) -> kryo.addDefaultSerializer(clazz, customSerializer) }

    private fun <T : Any> CheckpointSerializationContext.kryo(task: Kryo.() -> T): T {
        return getPool(this).run { kryo ->
            kryo.context.ensureCapacity(properties.size)
            properties.forEach { kryo.context.put(it.key, it.value) }
            try {
                kryo.task()
            } finally {
                kryo.context.clear()
            }
        }
    }

    override fun <T : Any> deserialize(
        byteSequence: ByteSequence,
        clazz: Class<T>,
        context: CheckpointSerializationContext
    ): T {
        val dataBytes = kryoMagic.consume(byteSequence)
            ?: throw KryoException("Serialized bytes header does not match expected format.")
        return context.kryo {
            kryoInput(ByteBufferInputStream(dataBytes)) {
                val result: T
                loop@ while (true) {
                    when (SectionId.reader.readFrom(this)) {
                        SectionId.ENCODING -> {
                            val encoding = CordaSerializationEncoding.reader.readFrom(this)
                            context.encodingWhitelist.acceptEncoding(encoding) || throw KryoException(
                                ENCODING_NOT_PERMITTED_FORMAT.format(encoding)
                            )
                            substitute(encoding::decompress)
                        }
                        SectionId.DATA_AND_STOP, SectionId.ALT_DATA_AND_STOP -> {
                            result = if (context.objectReferencesEnabled) {
                                uncheckedCast(readClassAndObject(this))
                            } else {
                                withoutReferences { uncheckedCast<Any?, T>(readClassAndObject(this)) }
                            }
                            break@loop
                        }
                    }
                }
                result
            }
        }
    }

    override fun <T : Any> serialize(obj: T, context: CheckpointSerializationContext): ByteArray {
        return context.kryo {
            kryoOutput {
                kryoMagic.writeTo(this)
                context.encoding?.let { encoding ->
                    SectionId.ENCODING.writeTo(this)
                    (encoding as CordaSerializationEncoding).writeTo(this)
                    substitute(encoding::compress)
                }
                SectionId.ALT_DATA_AND_STOP.writeTo(this) // Forward-compatible in null-encoding case.
                if (context.objectReferencesEnabled) {
                    writeClassAndObject(this, obj)
                } else {
                    withoutReferences { writeClassAndObject(this, obj) }
                }
            }
        }
    }
}



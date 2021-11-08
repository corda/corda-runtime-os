package net.corda.internal.serialization.amqp

import net.corda.v5.base.util.uncheckedCast
import net.corda.v5.serialization.SerializationContext
import net.corda.v5.serialization.SerializationCustomSerializer
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.Type
import kotlin.reflect.full.allSupertypes
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure

/**
 * Index into the types list of the parent type of the serializer object, should be the
 * type that this object proxies for
 */
private const val CORDAPP_TYPE = 0

/**
 * Index into the types list of the parent type of the serializer object, should be the
 * type of the proxy object that we're using to represent the object we're proxying for
 */
private const val PROXY_TYPE = 1

/**
 * Wrapper class for user provided serializers
 *
 * Through the CorDapp JAR scanner we will have a list of custom serializer types that implement
 * the toProxy and fromProxy methods. This class takes an instance of one of those objects and
 * embeds it within a serialization context associated with a serializer factory by creating
 * and instance of this class and registering that with a [SerializerFactory]
 *
 * Proxy serializers should transform an unserializable class into a representation that we can serialize
 *
 * @property serializer in instance of a user written serialization proxy, normally scanned and loaded
 * automatically
 * @property type the Java [Type] of the class which this serializes, inferred via reflection of the
 * [serializer]'s super type
 * @property proxyType the Java [Type] of the class into which instances of [type] are proxied for use by
 * the underlying serialization engine
 *
 * @param withInheritance should the serializer work for this type and all inheriting classes? Allows serializers for
 * interfaces and abstract classes. Always set to false for CorDapp defined serializers
 */
internal class CorDappCustomSerializer(
    private val serializer: SerializationCustomSerializer<*, *>,
    private val withInheritance: Boolean
) : AMQPSerializer<Any>, SerializerFor {

    override val type: Type
    private val proxyType: Type

    init {
        val types = serializer.serializerTypes()
        type = types.targetType
        proxyType = types.proxyType
    }

    override val typeDescriptor: Symbol = typeDescriptorFor(type)
    val descriptor: Descriptor = Descriptor(typeDescriptor)

    override fun writeClassInfo(output: SerializationOutput, context: SerializationContext) {}

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput,
                             context: SerializationContext, debugIndent: Int
    ) {
        val proxy = uncheckedCast<SerializationCustomSerializer<*, *>,
                SerializationCustomSerializer<Any?, Any?>>(serializer).toProxy(obj)
            ?: throw NotSerializableException("proxy object is null")

        data.withDescribed(descriptor) {
            output.writeObject(proxy, data, proxyType, context)
        }
    }

    override fun readObject(obj: Any, serializationSchemas: SerializationSchemas, metadata: Metadata,
                            input: DeserializationInput, context: SerializationContext
    ): Any {
        val proxy = input.readObject(obj, serializationSchemas, metadata, proxyType, context)
        return uncheckedCast<SerializationCustomSerializer<*, *>,
                SerializationCustomSerializer<Any?, Any?>>(serializer).fromProxy(proxy)!!
    }

    /**
     * For 3rd party plugin serializers we are going to exist on exact type matching. i.e. we will
     * not support base class serializers for derivedtypes
     */
    override fun isSerializerFor(clazz: Class<*>) =
        if (withInheritance) type.asClass().isAssignableFrom(clazz) else type.asClass() == clazz

    override fun toString(): String = "${this::class.java}(${serializer::class.java})"
}

internal data class CustomSerializerTypes(val targetType: Type, val proxyType: Type)

internal fun SerializationCustomSerializer<*, *>.serializerTypes(): CustomSerializerTypes {
    val types = this::class.allSupertypes.filter { it.jvmErasure == SerializationCustomSerializer::class }
        .flatMap { it.arguments }
        .map { it.type!!.javaType }

    if (types.size != 2) {
        throw AMQPNotSerializableException(
            CorDappCustomSerializer::class.java,
            "Unable to determine serializer parent types")
    }

    return CustomSerializerTypes(types[CORDAPP_TYPE], types[PROXY_TYPE])
}

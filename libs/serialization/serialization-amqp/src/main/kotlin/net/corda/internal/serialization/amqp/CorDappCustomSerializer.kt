package net.corda.internal.serialization.amqp

import com.google.common.reflect.TypeToken
import net.corda.v5.base.util.uncheckedCast
import net.corda.v5.serialization.SerializationContext
import net.corda.v5.serialization.SerializationCustomSerializer
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure

/**
 * Index into the types list of the parent type of the serializer object, should be the
 * type that this object proxies for
 */
const val CORDAPP_TYPE = 0

/**
 * Index into the types list of the parent type of the serializer object, should be the
 * type of the proxy object that we're using to represent the object we're proxying for
 */
const val PROXY_TYPE = 1

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
 * @param factory a [SerializerFactory] belonging to the context this serializer is being instantiated
 * for
 */
class CorDappCustomSerializer(
        private val serializer: SerializationCustomSerializer<*, *>,
        factory: SerializerFactory
) : AMQPSerializer<Any>, SerializerFor {
    override val revealSubclassesInSchema: Boolean get() = false

    private val types = serializer::class.supertypes.filter { it.jvmErasure == SerializationCustomSerializer::class }
            .flatMap { it.arguments }
            .map { it.type!!.javaType }

    init {
        if (types.size != 2) {
            throw AMQPNotSerializableException(
                    CorDappCustomSerializer::class.java,
                    "Unable to determine serializer parent types")
        }
    }

    override val type = types[CORDAPP_TYPE]
    val proxyType = types[PROXY_TYPE]
    override val typeDescriptor: Symbol = typeDescriptorFor(type)
    val descriptor: Descriptor = Descriptor(typeDescriptor)
    private val proxySerializer: ObjectSerializer by lazy {
        ObjectSerializer.make(factory.getTypeInformation(proxyType), factory)
    }

    override fun writeClassInfo(output: SerializationOutput) {}

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput,
                             context: SerializationContext, debugIndent: Int
    ) {
        val proxy = uncheckedCast<SerializationCustomSerializer<*, *>,
                SerializationCustomSerializer<Any?, Any?>>(serializer).toProxy(obj)

        data.withDescribed(descriptor) {
            data.withList {
                proxySerializer.propertySerializers.forEach { (_, serializer) ->
                    serializer.writeProperty(proxy, this, output, context, debugIndent)
                }
            }
        }
    }

    override fun readObject(obj: Any, serializationSchemas: SerializationSchemas, metadata: Metadata,
                            input: DeserializationInput, context: SerializationContext
    ) = uncheckedCast<SerializationCustomSerializer<*, *>, SerializationCustomSerializer<Any?, Any?>>(
            serializer).fromProxy(uncheckedCast(proxySerializer.readObject(obj, serializationSchemas, metadata, input, context)))!!

    /**
     * For 3rd party plugin serializers we are going to exist on exact type matching. i.e. we will
     * not support base class serializers for derivedtypes
     */
    override fun isSerializerFor(clazz: Class<*>) =
        TypeToken.of(type.asClass()) == TypeToken.of(clazz)

    override fun toString(): String = "${this::class.java}(${serializer::class.java})"
}


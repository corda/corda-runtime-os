package net.corda.internal.serialization.amqp.standard

import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.internal.serialization.amqp.AMQPSerializer
import net.corda.internal.serialization.amqp.SerializerFor
import net.corda.internal.serialization.amqp.AMQPNotSerializableException
import net.corda.internal.serialization.amqp.typeDescriptorFor
import net.corda.internal.serialization.amqp.Descriptor
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.withDescribed
import net.corda.internal.serialization.amqp.withList
import net.corda.internal.serialization.amqp.SerializationSchemas
import net.corda.internal.serialization.amqp.Metadata
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.asClass
import net.corda.serialization.SerializationContext
import net.corda.v5.serialization.SerializationCustomSerializer
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type
import java.lang.reflect.ParameterizedType

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
 * @property serializer an instance of a user written serialization proxy, normally scanned and loaded
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
    override val revealSubclassesInSchema: Boolean
        get() = false

    override val type: Type
    private val proxyType: Type

    init {
        // Discover generic type parameter values using Java reflection,
        // which is more reliable than Kotlin reflection with OSGi.
        // We require that the CorDapp's custom serializer implements
        // SerializationCustomSerializer directly, but so does our
        // CorDapp byte-code scanner.
        val types = serializer::class.java.genericInterfaces
            .filterIsInstance<ParameterizedType>()
            .filter { it.rawType === SerializationCustomSerializer::class.java }
            .flatMap { it.actualTypeArguments.asList() }

        if (types.size != 2) {
            throw AMQPNotSerializableException(
                CorDappCustomSerializer::class.java,
                "Unable to determine serializer parent types")
        }

        type = types[CORDAPP_TYPE]
        proxyType = types[PROXY_TYPE]
    }

    private val proxySerializer: ObjectSerializer by lazy(LazyThreadSafetyMode.PUBLICATION) {
        ObjectSerializer.make(factory.getTypeInformation(proxyType), factory)
    }

    override val typeDescriptor: Symbol = typeDescriptorFor(type)
    override fun writeClassInfo(output: SerializationOutput, context: SerializationContext) {}

    val descriptor: Descriptor = Descriptor(typeDescriptor)

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput,
                             context: SerializationContext, debugIndent: Int
    ) {
        @Suppress("unchecked_cast")
        val proxy = (serializer as SerializationCustomSerializer<Any, Any>).toProxy(obj)

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
    ): Any {
        val proxy = proxySerializer.readObject(obj, serializationSchemas, metadata, input, context)
        @Suppress("unchecked_cast")
        return (serializer as SerializationCustomSerializer<Any, Any>).fromProxy(proxy)
    }

    /**
     * For 3rd party plugin serializers we are going to insist on exact type matching,
     * i.e. we will not support base class serializers for derived types.
     */
    override fun isSerializerFor(clazz: Class<*>) = type.asClass() === clazz

    override fun toString(): String = "${this::class.java}(${serializer::class.java})"
}

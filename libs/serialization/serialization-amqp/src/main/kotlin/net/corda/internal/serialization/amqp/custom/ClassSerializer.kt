package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.SerializationContext
import net.corda.internal.serialization.amqp.AMQPNotSerializableException
import net.corda.internal.serialization.amqp.CustomSerializer
import net.corda.internal.serialization.amqp.LocalSerializerFactory
import net.corda.internal.serialization.amqp.custom.ClassSerializer.ClassProxy
import net.corda.internal.serialization.osgi.TypeResolver
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.trace

/**
 * A serializer for [Class] that uses [ClassProxy] proxy object to write out
 */
class ClassSerializer(
    factory: LocalSerializerFactory
) : CustomSerializer.Proxy<Class<*>, ClassProxy>(
    Class::class.java,
    ClassProxy::class.java,
    factory,
    withInheritance = false
) {
    companion object {
        private val logger = contextLogger()
    }

    override fun toProxy(obj: Class<*>, context: SerializationContext): ClassProxy  {
        logger.trace { "serializer=custom, type=ClassSerializer, name=\"${obj.name}\", action=toProxy" }
        return ClassProxy(obj.name)
    }

    override fun fromProxy(proxy: ClassProxy): Class<*> {
        logger.trace { "serializer=custom, type=ClassSerializer, name=\"${proxy.className}\", action=fromProxy" }

        return try {
            TypeResolver.resolve(proxy.className, proxy::class.java.classLoader)
        } catch (e: ClassNotFoundException) {
            throw AMQPNotSerializableException(
                type,
                "Could not instantiate ${proxy.className} - not on the classpath",
                "${proxy.className} was not found by the node, check the Node containing the CorDapp that " +
                        "implements ${proxy.className} is loaded and on the Classpath",
                mutableListOf(proxy.className))
        }
    }

    data class ClassProxy(val className: String)
}

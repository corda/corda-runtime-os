package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.AMQPNotSerializableException
import net.corda.internal.serialization.osgi.TypeResolver
import net.corda.serialization.InternalDirectSerializer
import net.corda.serialization.InternalDirectSerializer.ReadObject
import net.corda.serialization.InternalDirectSerializer.WriteObject
import net.corda.serialization.SerializationContext
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.trace

/**
 * A serializer for [Class] writes the fully-qualified class name.
 */
class ClassSerializer : InternalDirectSerializer<Class<*>> {
    override val type: Class<Class<*>> get() = Class::class.java
    override val withInheritance: Boolean get() = false

    companion object {
        private val logger = contextLogger()
    }

    override fun writeObject(obj: Class<*>, writer: WriteObject, context: SerializationContext)  {
        logger.trace { "serializer=custom, type=ClassSerializer, name=\"${obj.name}\"" }
        return writer.putAsString(obj.name)
    }

    override fun readObject(reader: ReadObject, context: SerializationContext): Class<*> {
        val className = reader.getAs(String::class.java)
        logger.trace { "serializer=custom, type=ClassSerializer, name=\"$className\"" }

        return try {
            TypeResolver.resolve(className, context.deserializationClassLoader)
        } catch (e: ClassNotFoundException) {
            throw AMQPNotSerializableException(
                type,
                "Could not instantiate $className - not on the classpath",
                "$className was not found by the node, check the Node containing the CorDapp that " +
                        "implements $className is loaded and on the Classpath",
                mutableListOf(className))
        }
    }
}

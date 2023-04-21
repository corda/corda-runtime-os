package net.corda.internal.serialization.amqp.standard

import net.corda.internal.serialization.amqp.LocalSerializerFactory
import net.corda.internal.serialization.amqp.AMQPSerializer
import net.corda.internal.serialization.amqp.TypeNotation
import net.corda.internal.serialization.amqp.RestrictedType
import net.corda.internal.serialization.amqp.AMQPTypeIdentifiers
import net.corda.internal.serialization.amqp.Descriptor
import net.corda.internal.serialization.amqp.Choice
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializationSchemas
import net.corda.internal.serialization.amqp.Metadata
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.asClass
import net.corda.internal.serialization.amqp.AMQPNotSerializableException
import net.corda.internal.serialization.amqp.withDescribed
import net.corda.internal.serialization.amqp.withList
import net.corda.serialization.SerializationContext
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type

/**
 * Our definition of an enum with the AMQP spec is a list (of two items, a string and an int) that is
 * a restricted type with a number of choices associated with it
 */
class EnumSerializer(declaredType: Type, declaredClass: Class<*>, factory: LocalSerializerFactory) :
    AMQPSerializer<Any> {
    override val type: Type = declaredType
    private val typeNotation: TypeNotation
    override val typeDescriptor = factory.createDescriptor(type)

    init {
        @Suppress("unchecked_cast")
        typeNotation = RestrictedType(
            AMQPTypeIdentifiers.nameForType(declaredType),
            null,
            emptyList(),
            "list",
            Descriptor(typeDescriptor),
            (declaredClass as Class<out Enum<*>>).enumConstants.zip(IntRange(0, declaredClass.enumConstants.size)).map {
                Choice(it.first.name, it.second.toString())
            }
        )
    }

    override fun writeClassInfo(output: SerializationOutput, context: SerializationContext) {
        output.writeTypeNotations(typeNotation)
    }

    override fun readObject(obj: Any, serializationSchemas: SerializationSchemas, metadata: Metadata,
                            input: DeserializationInput, context: SerializationContext): Any {
        val enumName = (obj as List<*>)[0] as String
        val enumOrd = obj[1] as Int
        val fromOrd = type.asClass().enumConstants[enumOrd] as Enum<*>?

        if (enumName != fromOrd?.name) {
            throw AMQPNotSerializableException(
                    type,
                    "Deserializing obj as enum $type with value $enumName.$enumOrd but ordinality has changed")
        }
        return fromOrd
    }

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput,
                             context: SerializationContext, debugIndent: Int) {
        if (obj !is Enum<*>) {
            throw AMQPNotSerializableException(type, "Serializing $obj as enum when it isn't")
        }

        data.withDescribed(typeNotation.descriptor) {
            withList {
                data.putString(obj.name)
                data.putInt(obj.ordinal)
            }
        }
    }
}
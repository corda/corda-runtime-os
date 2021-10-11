package net.corda.applications.examples.amqp.customserializer

import net.corda.internal.serialization.AMQP_STORAGE_CONTEXT
import net.corda.internal.serialization.AllWhitelist
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.v5.serialization.SerializationCustomSerializer

// Requirements:
// - Serialisation can support different serializers per sandbox group
//   - CorDapp provided
//   - Platform provided
// - Platform takes priority over CorDapp provided
// - Log message warning written if CorDapp attempts to replace platform serializer
// - Register the list of internal serializers for use by the sandbox
// - Register the list of CorDapp custom serializers for use by the sandbox

fun main() {
    val internalCustomSerializers = listOf<SerializationCustomSerializer<*, *>>(CustomSerializerA())
    val cordappCustomSerializers = listOf<SerializationCustomSerializer<*, *>>(CustomSerializerB())
    val factory = serializerFactory(internalCustomSerializers, cordappCustomSerializers)
    val serializationOutput = SerializationOutput(factory)
    val deserializationInput = DeserializationInput(factory)

    val testObject = NeedsCustomSerializerExampleA(10)
    val serializedBytes = serializationOutput.serialize(testObject, AMQP_STORAGE_CONTEXT)

    val deserializedObject = deserializationInput.deserialize(serializedBytes, AMQP_STORAGE_CONTEXT)

    assert(testObject.b == deserializedObject.b)
    println(testObject)
    println(deserializedObject)
}

private fun serializerFactory(
    internalCustomSerializers: List<SerializationCustomSerializer<*, *>>,
    cordappCustomSerializers: List<SerializationCustomSerializer<*, *>>
): SerializerFactory {
    val factory = SerializerFactoryBuilder.build(AllWhitelist)
    for (customSerializer in internalCustomSerializers) {
        factory.register(customSerializer, true)
    }
    for (customSerializer in cordappCustomSerializers) {
        factory.registerExternal(customSerializer)
    }
    return factory
}

fun differentSerializersPerSandboxGroup() {

}
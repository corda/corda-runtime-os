package net.corda.applications.examples.amqp.customserializer

import net.corda.internal.serialization.AMQP_STORAGE_CONTEXT
import net.corda.internal.serialization.AllWhitelist
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.internal.serialization.custom.PrivateKeySerializer
import net.corda.internal.serialization.custom.PublicKeySerializer
import net.corda.v5.serialization.SerializationCustomSerializer

// Requirements:
// - Serialisation can support different serializers per sandbox group
//   - CorDapp provided - This module
//   - Platform provided - Public/Private key
// - Platform takes priority over CorDapp provided
// - Log message warning written if CorDapp attempts to replace platform serializer
// - Register the list of internal serializers for use by the sandbox
// - Register the list of CorDapp custom serializers for use by the sandbox

val internalCustomSerializers = listOf<SerializationCustomSerializer<*, *>>(PrivateKeySerializer)
val cordappCustomSerializers = listOf<SerializationCustomSerializer<*, *>>(CustomSerializerA(), CustomSerializerB())

fun main() {
    differentSerializersPerSandboxGroup()
    println("------------------------------------------------")
    platformTakesPriority()
    println("------------------------------------------------")
    logMessageIfAttemptToReplacePlatform()
    println("------------------------------------------------")
    registerInternalSerializers()
    println("------------------------------------------------")
    registerCorDappSerializers()
    println("------------------------------------------------")


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

/**
 * Prove that we can have two different configurations of serialisation in memory at the same time
 * with different custom serialisers
 */
private fun differentSerializersPerSandboxGroup() {

    println("Building serialisation environments with different custom serialisers")
    val sandboxA = serializerFactory(internalCustomSerializers, listOf(CustomSerializerA()))
    val sandboxB = serializerFactory(internalCustomSerializers, listOf(CustomSerializerB()))

    val outputA = SerializationOutput(sandboxA)
    val outputB = SerializationOutput(sandboxB)

    val inputA = DeserializationInput(sandboxA)
    val inputB = DeserializationInput(sandboxB)

    println("Check custom serialisers work in environment A")
    val objA = NeedsCustomSerializerExampleA(1)
    val serializedBytesA = outputA.serialize(objA, AMQP_STORAGE_CONTEXT)
    println("SUCCESS - Serialise successful in environment A")
    val deserializeA = inputA.deserialize(serializedBytesA, AMQP_STORAGE_CONTEXT)
    println("SUCCESS - Deserialise successful in environment A")
    println("Original object: $objA")
    println("Deserialised object: $deserializeA")


    println("Check custom serialisers work in environment B")
    val objB = NeedsCustomSerializerExampleB(2)
    val serializedBytesB = outputB.serialize(objB, AMQP_STORAGE_CONTEXT)
    println("SUCCESS - Serialise successful in environment B")
    val deserializeB = inputB.deserialize(serializedBytesB, AMQP_STORAGE_CONTEXT)
    println("SUCCESS - Deserialise successful in environment B")
    println("Original object: $objB")
    println("Deserialised object: $deserializeB")

    println("Check that environments are independent")
    var worked = false
    try {
        outputA.serialize(objB, AMQP_STORAGE_CONTEXT)
    } catch (e: Exception) {
        println("SUCCESS - Environment A does not have serializer from environment B")
        worked = true
    } finally {
        if (!worked)
            println("FAIL - Environment A has serializer from environment B")
        worked = false
    }

    try {
        outputB.serialize(objA, AMQP_STORAGE_CONTEXT)
    } catch (e: Exception) {
        println("SUCCESS - Environment B does not have serializer from environment A")
        worked = true
    } finally {
        if (!worked)
            println("FAIL - Environment B has serializer from environment A")
    }
}

private fun platformTakesPriority() {
    println("Check that platform serialisers take priority over CorDapp serialisers")

    val factory = serializerFactory(listOf(CustomSerializerA(), CustomSerializerA()), emptyList())
    val output = SerializationOutput(factory)
    val input = DeserializationInput(factory)

    val obj = NeedsCustomSerializerExampleA(5)

    val serializedBytes = output.serialize(obj, AMQP_STORAGE_CONTEXT)
    val result = input.deserialize(serializedBytes, AMQP_STORAGE_CONTEXT)

    println("Original object: $obj")
    println("Deserialised object: $result")
}

private fun logMessageIfAttemptToReplacePlatform() {

}

private fun registerInternalSerializers() {

}
private fun registerCorDappSerializers() {

}

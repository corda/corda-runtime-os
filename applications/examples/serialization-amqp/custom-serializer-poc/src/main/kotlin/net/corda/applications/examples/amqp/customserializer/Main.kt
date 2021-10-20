package net.corda.applications.examples.amqp.customserializer

import net.corda.internal.serialization.AMQP_STORAGE_CONTEXT
import net.corda.internal.serialization.AllWhitelist
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.DuplicateCustomSerializerException
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.v5.serialization.MissingSerializerException
import net.corda.v5.serialization.SerializationCustomSerializer

// Requirements:
// - Serialisation can support different serializers per sandbox group
//   - CorDapp provided - This module
//   - Platform provided - Public/Private key
// - Platform takes priority over CorDapp provided
// - Log message warning written if CorDapp attempts to replace platform serializer
// - Register the list of internal serializers for use by the sandbox
// - Register the list of CorDapp custom serializers for use by the sandbox

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
}

private fun configureSerialization(
    internalCustomSerializers: List<SerializationCustomSerializer<*, *>>,
    cordappCustomSerializers: List<SerializationCustomSerializer<*, *>>
): SerializerFactory {
    // Create SerializerFactory
    val factory = SerializerFactoryBuilder.build(AllWhitelist)
    // Register platform serializers
    for (customSerializer in internalCustomSerializers) {
        factory.register(customSerializer, true)
    }
    // Register CorDapp serializers
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

    println("REQUIREMENT - Building serialisation environments with different custom serialisers")
    val sandboxA = configureSerialization(emptyList(), listOf(CustomSerializerA()))
    val sandboxB = configureSerialization(emptyList(), listOf(CustomSerializerB()))

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
    } catch (e: MissingSerializerException) {
        println("SUCCESS - Environment A does not have serializer from environment B")
        worked = true
    } finally {
        if (!worked)
            println("FAIL - Environment A has serializer from environment B")
        worked = false
    }

    try {
        outputB.serialize(objA, AMQP_STORAGE_CONTEXT)
    } catch (e: MissingSerializerException) {
        println("SUCCESS - Environment B does not have serializer from environment A")
        worked = true
    } finally {
        if (!worked)
            println("FAIL - Environment B has serializer from environment A")
    }
}

private fun platformTakesPriority() {
    println("REQUIREMENT - Check that platform serialisers take priority over CorDapp serialisers")
    println("Difference from my earlier expectation - Throws exception instead of priority/log message")
    println("Only when we attempt to work with serialiser target type")
    println("This is stricter than I expected and comes out of existing behaviour. I believe this is acceptable.")

    println("Attempt to override platform serialiser:")

    val factory = configureSerialization(listOf(CustomSerializerA()), listOf(CustomSerializerA()))
    val output = SerializationOutput(factory)

    val obj = NeedsCustomSerializerExampleA(5)

    var worked = false
    try {
        output.serialize(obj, AMQP_STORAGE_CONTEXT)
    } catch (e: DuplicateCustomSerializerException) {
        println("SUCCESS - Exception thrown attempting to replace platform serialiser:")
        println(e.message)
        worked = true
    }
    finally {
        if (!worked)
            println("FAIL - System didn't notice we replaced platform serialiser")
    }

}

private fun logMessageIfAttemptToReplacePlatform() {
    println("REQUIREMENT - Log message warning written if CorDapp attempts to replace platform serializer")
    println("- Throws exception instead")
}

private fun registerInternalSerializers() {
    println("REQUIREMENT - Register the list of internal serializers for use by the sandbox")
    println("- See configureSerialization for example")
}

private fun registerCorDappSerializers() {
    println("REQUIREMENT - Register the list of CorDapp custom serializers for use by the sandbox")
    println("- See configureSerialization for example")
}

package net.corda.internal.serialization.amqp.helper

import net.corda.crypto.impl.serialization.PublicKeySerializer
import net.corda.internal.serialization.SerializationServiceImpl
import net.corda.internal.serialization.amqp.DefaultDescriptorBasedSerializerRegistry
import net.corda.internal.serialization.amqp.DescriptorBasedSerializerRegistry
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.internal.serialization.amqp.currentSandboxGroup
import net.corda.internal.serialization.registerCustomSerializers
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.cipher.suite.CipherSchemeMetadata

private fun testDefaultFactoryNoEvolution(
    registerMoreSerializers: (it: SerializerFactory) -> Unit,
    schemeMetadata: CipherSchemeMetadata,
    descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry =
        DefaultDescriptorBasedSerializerRegistry(),
): SerializerFactory =
    SerializerFactoryBuilder.build(
        testSerializationContext.currentSandboxGroup(),
        descriptorBasedSerializerRegistry = descriptorBasedSerializerRegistry,
        allowEvolution = false
    ).also {
        registerCustomSerializers(it)
        it.register(PublicKeySerializer(schemeMetadata), it)
        registerMoreSerializers(it)
    }

class TestSerializationService {
    companion object{
        fun getTestSerializationService(
            registerMoreSerializers: (it: SerializerFactory) -> Unit,
            schemeMetadata: CipherSchemeMetadata
        ) : SerializationService {
            val factory = testDefaultFactoryNoEvolution(registerMoreSerializers, schemeMetadata)
            val output = SerializationOutput(factory)
            val input = DeserializationInput(factory)
            val context = testSerializationContext

            return SerializationServiceImpl(output, input, context)
        }
    }
}
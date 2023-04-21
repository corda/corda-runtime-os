package net.corda.internal.serialization.amqp.helper

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
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

class TestSerializationService {
    companion object{
        private fun getTestDefaultFactoryNoEvolution(
            registerMoreSerializers: (it: SerializerFactory) -> Unit,
            cipherSchemeMetadata: CipherSchemeMetadata,
            descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry =
                DefaultDescriptorBasedSerializerRegistry(),
        ): SerializerFactory =
            SerializerFactoryBuilder.build(
                testSerializationContext.currentSandboxGroup(),
                descriptorBasedSerializerRegistry = descriptorBasedSerializerRegistry,
                allowEvolution = false
            ).also {
                registerCustomSerializers(it)
                it.register(PublicKeySerializer(cipherSchemeMetadata), it)
                registerMoreSerializers(it)
            }

        fun getTestSerializationService(
            registerMoreSerializers: (it: SerializerFactory) -> Unit,
            cipherSchemeMetadata: CipherSchemeMetadata
        ) : SerializationService {
            val factory = getTestDefaultFactoryNoEvolution(registerMoreSerializers, cipherSchemeMetadata)
            val output = SerializationOutput(factory)
            val input = DeserializationInput(factory)
            val context = testSerializationContext

            return SerializationServiceImpl(output, input, context)
        }
    }
}
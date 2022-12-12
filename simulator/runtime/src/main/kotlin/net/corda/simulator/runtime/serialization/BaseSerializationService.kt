package net.corda.simulator.runtime.serialization

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.impl.serialization.PublicKeySerializer
import net.corda.internal.serialization.SerializationContextImpl
import net.corda.internal.serialization.SerializationServiceImpl
import net.corda.internal.serialization.amqp.DefaultDescriptorBasedSerializerRegistry
import net.corda.internal.serialization.amqp.DescriptorBasedSerializerRegistry
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.internal.serialization.amqp.amqpMagic
import net.corda.internal.serialization.registerCustomSerializers
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.SerializationContext
import net.corda.v5.application.serialization.SerializationService
import org.osgi.framework.Bundle

class BaseSerializationService : SerializationService by createSerializationService({}, CipherSchemeMetadataImpl()) {
    companion object{
        private fun buildDefaultFactoryNoEvolution(
            registerMoreSerializers: (it: SerializerFactory) -> Unit,
            schemeMetadata: CipherSchemeMetadata,
            sandboxGroup: SandboxGroup,
            descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry =
                DefaultDescriptorBasedSerializerRegistry(),
        ): SerializerFactory =
            SerializerFactoryBuilder.build(
                sandboxGroup,
                descriptorBasedSerializerRegistry = descriptorBasedSerializerRegistry,
                allowEvolution = false
            ).also {
                registerCustomSerializers(it)
                it.register(PublicKeySerializer(schemeMetadata), it)
                registerMoreSerializers(it)
            }

        fun createSerializationService(
            registerMoreSerializers: (it: SerializerFactory) -> Unit,
            schemeMetadata: CipherSchemeMetadata
        ): SerializationService {
            val context = SerializationContextImpl(
                preferredSerializationVersion = amqpMagic,
                properties = mutableMapOf(),
                objectReferencesEnabled = false,
                useCase = SerializationContext.UseCase.Testing,
                encoding = null,
                sandboxGroup = SimSandboxGroup()
            )
            val factory = buildDefaultFactoryNoEvolution(
                registerMoreSerializers,
                schemeMetadata,
                context.sandboxGroup as SandboxGroup
            )
            val output = SerializationOutput(factory)
            val input = DeserializationInput(factory)

            return SerializationServiceImpl(output, input, context)
        }

        private class SimSandboxGroup(
            private val classLoader: ClassLoader = ClassLoader.getSystemClassLoader()
        ) : SandboxGroup {

            override val metadata: Map<Bundle, CpkMetadata> = emptyMap()

            override fun loadClassFromMainBundles(className: String): Class<*> =
                Class.forName(className, false, classLoader)
            override fun <T : Any> loadClassFromMainBundles(className: String, type: Class<T>): Class<out T> =
                Class.forName(className, false, classLoader).asSubclass(type)
            override fun getClass(className: String, serialisedClassTag: String): Class<*> = Class.forName(className)
            override fun loadClassFromPublicBundles(className: String): Class<*> =
                Class.forName(className, false, classLoader)

            override fun getStaticTag(klass: Class<*>) = "S;simulator-not-using-osgi-bundle;not-in-a-sandbox"
            override fun getEvolvableTag(klass: Class<*>) = "E;simulator-not-using-osgi-bundle;not-in-a-sandbox"
        }

    }
}



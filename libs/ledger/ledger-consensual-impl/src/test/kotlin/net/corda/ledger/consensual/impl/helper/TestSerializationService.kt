package net.corda.ledger.consensual.impl.helper

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
import net.corda.internal.serialization.amqp.currentSandboxGroup
import net.corda.internal.serialization.registerCustomSerializers
import net.corda.ledger.consensual.impl.PartySerializer
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.SerializationContext
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.cipher.suite.KeyEncodingService
import org.osgi.framework.Bundle

//TODO(Deduplicate with net.corda.internal.serialization.amqp.testutils)

private class MockSandboxGroup(private val classLoader: ClassLoader = ClassLoader.getSystemClassLoader()) :
    SandboxGroup {
    override val metadata: Map<Bundle, CpkMetadata> = emptyMap()

    override fun loadClassFromMainBundles(className: String): Class<*> =
        Class.forName(className, false, classLoader)
    override fun <T : Any> loadClassFromMainBundles(className: String, type: Class<T>): Class<out T> =
        Class.forName(className, false, classLoader).asSubclass(type)
    override fun getClass(className: String, serialisedClassTag: String): Class<*> = Class.forName(className)
    override fun getStaticTag(klass: Class<*>): String = "S;bundle;sandbox"
    override fun getEvolvableTag(klass: Class<*>) = "E;bundle;sandbox"
}

private val testSerializationContext = SerializationContextImpl(
    preferredSerializationVersion = amqpMagic,
    properties = mutableMapOf(),
    objectReferencesEnabled = false,
    useCase = SerializationContext.UseCase.Testing,
    encoding = null,
    sandboxGroup = MockSandboxGroup()
)

private fun testDefaultFactoryNoEvolution(
    keyEncodingService: KeyEncodingService,
    descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry =
        DefaultDescriptorBasedSerializerRegistry()
): SerializerFactory =
    SerializerFactoryBuilder.build(
        testSerializationContext.currentSandboxGroup(),
        descriptorBasedSerializerRegistry = descriptorBasedSerializerRegistry,
        allowEvolution = false
    ).also {
        registerCustomSerializers(it)
        it.register(PublicKeySerializer(keyEncodingService), it)
        it.register(PartySerializer(), it)
}

class TestSerializationService {
    companion object{
        fun getTestSerializationService(keyEncodingService: KeyEncodingService) : SerializationService {
            val factory = testDefaultFactoryNoEvolution(keyEncodingService)
            val output = SerializationOutput(factory)
            val input = DeserializationInput(factory)
            val context = testSerializationContext

            return SerializationServiceImpl(output, input, context)
        }
    }
}
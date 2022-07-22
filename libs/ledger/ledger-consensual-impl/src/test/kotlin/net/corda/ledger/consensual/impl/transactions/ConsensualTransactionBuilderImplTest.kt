package net.corda.ledger.consensual.impl.transactions

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.merkle.MerkleTreeFactoryImpl
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
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.SerializationContext
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.ledger.consensual.ConsensualState
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.osgi.framework.Bundle
import java.security.SecureRandom
import java.time.Instant

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

@JvmField
val testSerializationContext = SerializationContextImpl(
    preferredSerializationVersion = amqpMagic,
    properties = mutableMapOf(),
    objectReferencesEnabled = false,
    useCase = SerializationContext.UseCase.Testing,
    encoding = null,
    sandboxGroup = MockSandboxGroup()
)

@JvmOverloads
fun testDefaultFactoryNoEvolution(
    descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry =
        DefaultDescriptorBasedSerializerRegistry()
): SerializerFactory =
    SerializerFactoryBuilder.build(
        testSerializationContext.currentSandboxGroup(),
        descriptorBasedSerializerRegistry = descriptorBasedSerializerRegistry,
        allowEvolution = false
    )

internal class ConsensualTransactionBuilderImplTest{
    companion object {
        private lateinit var digestService: DigestService
        private lateinit var merkleTreeFactory: MerkleTreeFactory
        private lateinit var secureRandom: SecureRandom
        private lateinit var serializer: SerializationService

        @BeforeAll
        @JvmStatic
        fun setup() {
            val schemeMetadata: CipherSchemeMetadata = CipherSchemeMetadataImpl()
            digestService = DigestServiceImpl(schemeMetadata, null)
            secureRandom = schemeMetadata.secureRandom
            merkleTreeFactory = MerkleTreeFactoryImpl(digestService)

            val factory = testDefaultFactoryNoEvolution()
            val output = SerializationOutput(factory)
            val input = DeserializationInput(factory)
            val context = testSerializationContext

            serializer = SerializationServiceImpl(output, input, context)
        }
    }
    @Test
    fun `cannot build empty Transaction`() {
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            ConsensualTransactionBuilderImpl().build(merkleTreeFactory, digestService, secureRandom, serializer)
        }
    }
    @Test
    fun `can build a simple Transaction`() {
        val tx = ConsensualTransactionBuilderImpl()
            .withTimeStamp(Instant.now())
            .withMetadata(ConsensualTransactionMetaDataImpl("ConsensualLedger", "v0.01", emptyList()))
            .build(merkleTreeFactory, digestService, secureRandom, serializer)
        println(tx.id)
    }
}
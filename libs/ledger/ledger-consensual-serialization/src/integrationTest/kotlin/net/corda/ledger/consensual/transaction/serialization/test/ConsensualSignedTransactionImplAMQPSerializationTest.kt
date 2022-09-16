package net.corda.ledger.consensual.transaction.serialization.test

import net.corda.crypto.impl.serialization.PublicKeySerializer
import net.corda.internal.serialization.AMQP_STORAGE_CONTEXT
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.ObjectAndEnvelope
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.internal.serialization.amqp.helper.TestSerializationService
import net.corda.internal.serialization.registerCustomSerializers
import net.corda.ledger.common.transaction.serialization.internal.WireTransactionSerializer
import net.corda.ledger.consensual.impl.PartySerializer
import net.corda.ledger.consensual.testkit.ConsensualSignedTransactionImplExample.Companion.getConsensualSignedTransactionImpl
import net.corda.ledger.consensual.transaction.serialization.internal.ConsensualSignedTransactionImplSerializer
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.SerializationContext
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.BundleContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.io.NotSerializableException
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@Component(service = [ SandboxFactory::class ])
class SandboxFactory @Activate constructor(
    @Reference
    private val sandboxCreationService: SandboxCreationService
) {
    fun loadSandboxGroup(): SandboxGroup {
        return sandboxCreationService.createSandboxGroup(emptyList())
    }

    fun unloadSandboxGroup(sandboxGroup: SandboxGroup) {
        sandboxCreationService.unloadSandboxGroup(sandboxGroup)
    }
}

@Timeout(value = 30, unit = TimeUnit.SECONDS)
@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(PER_CLASS)
class ConsensualSignedTransactionImplAMQPSerializationTest {
    private val testSerializationContext = AMQP_STORAGE_CONTEXT

    @RegisterExtension
    private val lifecycle = EachTestLifecycle()

    @InjectService(timeout = 1000)
    lateinit var digestService: DigestService

    @InjectService(timeout = 1000)
    lateinit var schemeMetadata: CipherSchemeMetadata

    @InjectService(timeout = 1000)
    lateinit var merkleTreeFactory: MerkleTreeFactory

    @InjectService(timeout = 1000)
    lateinit var jsonMarshallingService: JsonMarshallingService

    private lateinit var sandboxFactory: SandboxFactory

    @BeforeAll
    fun setUp(
        @InjectService(timeout = 1000)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        testDirectory: Path

    ) {
        sandboxSetup.configure(bundleContext, testDirectory)
        lifecycle.accept(sandboxSetup) { setup ->
            sandboxFactory = setup.fetchService(timeout = 1500)
        }
    }

    private fun testDefaultFactory(sandboxGroup: SandboxGroup, serializationService: SerializationService): SerializerFactory =
        SerializerFactoryBuilder.build(sandboxGroup, allowEvolution = true).also{
            registerCustomSerializers(it)
            it.register(PublicKeySerializer(schemeMetadata), it)
            it.register(PartySerializer(), it)
            it.register(WireTransactionSerializer(merkleTreeFactory, digestService, jsonMarshallingService), it)
            it.register(ConsensualSignedTransactionImplSerializer(serializationService), it)
        }

    @Throws(NotSerializableException::class)
    inline fun <reified T : Any> DeserializationInput.deserializeAndReturnEnvelope(
            bytes: SerializedBytes<T>,
            serializationContext: SerializationContext
    ): ObjectAndEnvelope<T> = deserializeAndReturnEnvelope(bytes, T::class.java, serializationContext)

    @Test
    fun `successfully deserialise when composed bundle class is installed`() {
        // Create sandbox group
        val sandboxGroup = sandboxFactory.loadSandboxGroup()

        val serializationServiceNullCfg = TestSerializationService.getTestSerializationService({
            it.register(PartySerializer(), it)
            it.register(WireTransactionSerializer(
                merkleTreeFactory,
                digestService,
                jsonMarshallingService
            ), it)
        } , schemeMetadata)

        val serializationService = TestSerializationService.getTestSerializationService({
            it.register(PartySerializer(), it)
            it.register(WireTransactionSerializer(
                merkleTreeFactory,
                digestService,
                jsonMarshallingService
            ), it)
            it.register(ConsensualSignedTransactionImplSerializer(serializationServiceNullCfg), it)
        } , schemeMetadata)

        try {
            // Initialised two serialisation factories to avoid having successful tests due to caching
            val factory1 = testDefaultFactory(sandboxGroup, serializationService)
            val factory2 = testDefaultFactory(sandboxGroup, serializationService)

            // Initialise the serialisation context
            val testSerializationContext = testSerializationContext.withSandboxGroup(sandboxGroup)

            val signedTransaction = getConsensualSignedTransactionImpl(
                digestService,
                merkleTreeFactory,
                serializationService,
                jsonMarshallingService
            )
            val serialised = SerializationOutput(factory1).serialize(signedTransaction, testSerializationContext)

            // Perform deserialization and check if the correct class is deserialised
            val deserialized =
                DeserializationInput(factory2).deserializeAndReturnEnvelope(serialised, testSerializationContext)

            assertThat(deserialized.obj.javaClass.name).isEqualTo(
                "net.corda.ledger.consensual.impl.transaction.ConsensualSignedTransactionImpl")

            assertThat(deserialized.obj).isEqualTo(signedTransaction)
            Assertions.assertDoesNotThrow{
                deserialized.obj.id
            }
            assertThat(deserialized.obj.id).isEqualTo(signedTransaction.id)

        } finally {
            sandboxFactory.unloadSandboxGroup(sandboxGroup)
        }
    }
}

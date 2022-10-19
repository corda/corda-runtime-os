package net.corda.ledger.consensual.data.transaction.serializer.amqp.tests

import net.corda.internal.serialization.AMQP_STORAGE_CONTEXT
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.ObjectAndEnvelope
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.internal.serialization.amqp.helper.TestSerializationService
import net.corda.internal.serialization.registerCustomSerializers
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.consensual.testkit.getConsensualSignedTransaction
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.InternalCustomSerializer
import net.corda.serialization.SerializationContext
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.io.NotSerializableException
import java.nio.file.Path
import java.security.PublicKey
import java.util.concurrent.TimeUnit

@Timeout(value = 30, unit = TimeUnit.SECONDS)
@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(PER_CLASS)
class ConsensualSignedTransactionAMQPSerializationTest {
    private val testSerializationContext = AMQP_STORAGE_CONTEXT

    @RegisterExtension
    private val lifecycle = EachTestLifecycle()

    @InjectService(timeout = 1000)
    lateinit var digestService: DigestService

    @InjectService(timeout = 1000)
    lateinit var schemeMetadata: CipherSchemeMetadata

    @InjectService(timeout = 1000)
    lateinit var merkleTreeProvider: MerkleTreeProvider

    @InjectService(timeout = 1000)
    lateinit var jsonMarshallingService: JsonMarshallingService

    @InjectService(timeout = 1000)
    lateinit var digitalSignatureVerificationService: DigitalSignatureVerificationService

    @InjectService(timeout = 1000)
    lateinit var signingService: SigningService

    private lateinit var emptySandboxGroup: SandboxGroup
    private lateinit var publickeySerializer: InternalCustomSerializer<PublicKey>
    private lateinit var wireTransactionSerializer: InternalCustomSerializer<WireTransaction>
    private lateinit var consensualSignedTransactionSerializer: InternalCustomSerializer<ConsensualSignedTransaction>

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
            val sandboxCreationService = setup.fetchService<SandboxCreationService>(timeout = 1500)
            emptySandboxGroup = sandboxCreationService.createSandboxGroup(emptyList())
            setup.withCleanup {
                sandboxCreationService.unloadSandboxGroup(emptySandboxGroup)
            }
            publickeySerializer = setup.fetchService(
                "(component.name=net.corda.crypto.impl.serialization.PublicKeySerializer)",
                1500
            )
            wireTransactionSerializer = setup.fetchService(
                "(component.name=net.corda.ledger.common.data.transaction.serializer.amqp.WireTransactionSerializer)",
                1500
            )

            consensualSignedTransactionSerializer = setup.fetchService(
                "(component.name=net.corda.ledger.consensual.data.transaction.serializer.amqp.ConsensualSignedTransactionSerializer)",
                1500
            )
        }
    }

    private fun testDefaultFactory(sandboxGroup: SandboxGroup): SerializerFactory =
        SerializerFactoryBuilder.build(sandboxGroup, allowEvolution = true).also {
            registerCustomSerializers(it)
            it.register(publickeySerializer, it)
            it.register(wireTransactionSerializer, it)
            it.register(consensualSignedTransactionSerializer, it)
        }

    @Throws(NotSerializableException::class)
    inline fun <reified T : Any> DeserializationInput.deserializeAndReturnEnvelope(
        bytes: SerializedBytes<T>,
        serializationContext: SerializationContext
    ): ObjectAndEnvelope<T> = deserializeAndReturnEnvelope(bytes, T::class.java, serializationContext)

    @Test
    @Suppress("FunctionName")
    fun `successfully serialize and deserialize a Consensual Signed Transaction`() {
        // Create sandbox group

        val serializationService = TestSerializationService.getTestSerializationService({
            it.register(wireTransactionSerializer, it)
            it.register(consensualSignedTransactionSerializer, it)
        }, schemeMetadata)

        // Initialised two serialisation factories to avoid having successful tests due to caching
        val factory1 = testDefaultFactory(emptySandboxGroup)
        val factory2 = testDefaultFactory(emptySandboxGroup)

        // Initialise the serialisation context
        val testSerializationContext = testSerializationContext.withSandboxGroup(emptySandboxGroup)

        val signedTransaction = getConsensualSignedTransaction(
            digestService,
            merkleTreeProvider,
            serializationService,
            jsonMarshallingService,
            signingService,
            digitalSignatureVerificationService
        )
        val serialised = SerializationOutput(factory1).serialize(signedTransaction, testSerializationContext)


        // Perform deserialization and check if the correct class is deserialized
        val deserialized =
            DeserializationInput(factory2).deserializeAndReturnEnvelope(serialised, testSerializationContext)

        assertThat(deserialized.obj.javaClass.name)
            .isEqualTo("net.corda.ledger.consensual.data.transaction.ConsensualSignedTransactionImpl")

        assertThat(deserialized.obj)
            .isInstanceOf(ConsensualSignedTransaction::class.java)
            .isEqualTo(signedTransaction)

        assertDoesNotThrow {
            deserialized.obj.id
        }
        assertThat(deserialized.obj.id).isEqualTo(signedTransaction.id)
    }
}

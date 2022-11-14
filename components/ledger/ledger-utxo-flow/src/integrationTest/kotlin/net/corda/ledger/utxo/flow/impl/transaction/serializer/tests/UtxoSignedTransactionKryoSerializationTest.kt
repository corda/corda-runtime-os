package net.corda.ledger.utxo.flow.impl.transaction.serializer.tests

import net.corda.common.json.validation.JsonValidator
import net.corda.internal.serialization.amqp.helper.TestSerializationService
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.utxo.testkit.getUtxoSignedTransactionExample
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.checkpoint.CheckpointInternalCustomSerializer
import net.corda.serialization.checkpoint.factory.CheckpointSerializerBuilderFactory
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.AllTestsLifecycle
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.file.Path

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(PER_CLASS)
class UtxoSignedTransactionKryoSerializationTest {

    @InjectService(timeout = 1000)
    lateinit var checkpointSerializerBuilderFactory: CheckpointSerializerBuilderFactory

    @InjectService(timeout = 1000)
    lateinit var digestService: DigestService

    @InjectService(timeout = 1000)
    lateinit var merkleTreeProvider: MerkleTreeProvider

    @InjectService(timeout = 1000)
    lateinit var cipherSchemeMetadata: CipherSchemeMetadata

    @InjectService(timeout = 1000)
    lateinit var jsonMarshallingService: JsonMarshallingService

    @InjectService(timeout = 1000)
    lateinit var jsonValidator: JsonValidator

    @InjectService(timeout = 1000)
    lateinit var signingService: SigningService

    @InjectService(timeout = 1000)
    lateinit var digitalSignatureVerificationService: DigitalSignatureVerificationService

    private lateinit var emptySandboxGroup: SandboxGroup

    private lateinit var wireTransactionKryoSerializer: CheckpointInternalCustomSerializer<WireTransaction>
    private lateinit var utxoSignedTransactionSerializer: CheckpointInternalCustomSerializer<UtxoSignedTransaction>

    @BeforeAll
    fun setup(
        @InjectService(timeout = 1000)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        baseDirectory: Path
    ) {
        sandboxSetup.configure(bundleContext, baseDirectory)
        Companion.lifecycle.accept(sandboxSetup) { setup ->
            val sandboxCreationService = setup.fetchService<SandboxCreationService>(timeout = 1500)
            emptySandboxGroup = sandboxCreationService.createSandboxGroup(emptyList())
            setup.withCleanup {
                sandboxCreationService.unloadSandboxGroup(emptySandboxGroup)
            }
            wireTransactionKryoSerializer = setup.fetchService(
                "(component.name=net.corda.ledger.common.flow.impl.transaction.serializer.kryo.WireTransactionKryoSerializer)",
                1500
            )
            utxoSignedTransactionSerializer = setup.fetchService(
                "(component.name=net.corda.ledger.utxo.flow.impl.transaction.serializer.kryo.UtxoSignedTransactionKryoSerializer)",
                1500
            )
        }
    }

    @Test
    @Suppress("FunctionName")
    fun `correct serialization of a utxo Signed Transaction`() {
        val serializationService = TestSerializationService.getTestSerializationService({ }, cipherSchemeMetadata)

        val builder = checkpointSerializerBuilderFactory.createCheckpointSerializerBuilder(emptySandboxGroup)
        val kryoSerializer = builder
            .addSerializer(WireTransaction::class.java, wireTransactionKryoSerializer)
            .addSerializer(UtxoSignedTransaction::class.java, utxoSignedTransactionSerializer)
            .build()

        val signedTransaction = getUtxoSignedTransactionExample(
            digestService,
            merkleTreeProvider,
            serializationService,
            jsonMarshallingService,
            jsonValidator,
            signingService,
            digitalSignatureVerificationService
        )

        val bytes = kryoSerializer.serialize(signedTransaction)
        val deserialized = kryoSerializer.deserialize(bytes, UtxoSignedTransaction::class.java)

        assertThat(deserialized).isEqualTo(signedTransaction)
        Assertions.assertDoesNotThrow { deserialized.id }
        Assertions.assertEquals(signedTransaction.id, deserialized.id)
    }

    companion object {
        @JvmStatic
        @RegisterExtension
        private val lifecycle = AllTestsLifecycle()
    }
}

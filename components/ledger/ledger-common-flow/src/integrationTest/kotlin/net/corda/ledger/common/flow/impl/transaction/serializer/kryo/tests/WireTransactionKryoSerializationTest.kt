package net.corda.ledger.common.flow.impl.transaction.serializer.kryo.tests

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.testkit.getWireTransactionExample
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.checkpoint.CheckpointInternalCustomSerializer
import net.corda.serialization.checkpoint.factory.CheckpointSerializerBuilderFactory
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.AllTestsLifecycle
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
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
class WireTransactionKryoSerializationTest {
    @RegisterExtension
    private val lifecycle = AllTestsLifecycle()

    @InjectService(timeout = 1000)
    lateinit var checkpointSerializerBuilderFactory: CheckpointSerializerBuilderFactory

    @InjectService(timeout = 1000)
    lateinit var digestService: DigestService

    @InjectService(timeout = 1000)
    lateinit var merkleTreeProvider: MerkleTreeProvider

    @InjectService(timeout = 1000)
    lateinit var jsonMarshallingService: JsonMarshallingService

    private lateinit var emptySandboxGroup: SandboxGroup

    private lateinit var wireTransactionKryoSerializer: CheckpointInternalCustomSerializer<WireTransaction>

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
        lifecycle.accept(sandboxSetup) { setup ->
            val sandboxCreationService = setup.fetchService<SandboxCreationService>(timeout = 1500)
            emptySandboxGroup = sandboxCreationService.createSandboxGroup(emptyList())
            setup.withCleanup {
                sandboxCreationService.unloadSandboxGroup(emptySandboxGroup)
            }
            wireTransactionKryoSerializer = setup.fetchService(1500)
        }
    }

    @Test
    @Suppress("FunctionName")
    fun `correct serialization of a wire Transaction`() {
        val builder =
            checkpointSerializerBuilderFactory.createCheckpointSerializerBuilder(emptySandboxGroup)
        val kryoSerializer = builder
            .addSerializer(WireTransaction::class.java, wireTransactionKryoSerializer)
            .build()

        val wireTransaction = getWireTransactionExample(digestService, merkleTreeProvider, jsonMarshallingService)
        val bytes = kryoSerializer.serialize(wireTransaction)
        val deserialized = kryoSerializer.deserialize(bytes, WireTransaction::class.java)

        assertThat(deserialized).isEqualTo(wireTransaction)
        Assertions.assertDoesNotThrow {
            deserialized.id
        }
        Assertions.assertEquals(wireTransaction.id, deserialized.id)
    }
}

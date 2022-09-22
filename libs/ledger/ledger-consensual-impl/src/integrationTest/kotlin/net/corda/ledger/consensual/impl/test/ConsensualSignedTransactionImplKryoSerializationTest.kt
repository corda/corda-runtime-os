package net.corda.ledger.consensual.impl.test

import net.corda.internal.serialization.amqp.helper.TestSerializationService
import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.ledger.consensual.impl.transaction.ConsensualSignedTransactionImpl
import net.corda.ledger.consensual.testkit.getConsensualSignedTransactionImpl
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.InternalCustomSerializer
import net.corda.serialization.checkpoint.CheckpointInternalCustomSerializer
import net.corda.serialization.checkpoint.factory.CheckpointSerializerBuilderFactory
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.AllTestsLifecycle
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.ledger.consensual.Party
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
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.file.Path

@Component(service = [ SandboxManagementService::class ])
class SandboxManagementService @Activate constructor(
    @Reference
    private val sandboxCreationService: SandboxCreationService
) {
    val group1: SandboxGroup = sandboxCreationService.createSandboxGroup(emptyList())

    @Suppress("unused")
    @Deactivate
    fun cleanup() {
        sandboxCreationService.unloadSandboxGroup(group1)
    }
}

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
    lateinit var merkleTreeFactory: MerkleTreeFactory

    @InjectService(timeout = 1000)
    lateinit var schemeMetadata: CipherSchemeMetadata

    @InjectService(timeout = 1000)
    lateinit var jsonMarshallingService: JsonMarshallingService

    private lateinit var sandboxManagementService: SandboxManagementService

    private lateinit var wireTransactionKryoSerializer: CheckpointInternalCustomSerializer<WireTransaction>
    private lateinit var consensualSignedTransactionImplSeralizer: CheckpointInternalCustomSerializer<ConsensualSignedTransactionImpl>

    private lateinit var partySerializer: InternalCustomSerializer<Party>

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
            sandboxManagementService = setup.fetchService(timeout = 1500)
            partySerializer = setup.fetchService(
                "(component.name=net.corda.ledger.consensual.impl.PartySerializer)",
                1500
            )
            wireTransactionKryoSerializer = setup.fetchService(
                "(component.name=net.corda.ledger.common.impl.transaction.serializer.WireTransactionKryoSerializer)",
                1500
            )
            consensualSignedTransactionImplSeralizer = setup.fetchService(
                "(component.name=net.corda.ledger.consensual.impl.transaction.serializer.ConsensualSignedTransactionImplKryoSerializer)",
                1500
            )
        }
    }

    @Test
    @Suppress("FunctionName")
    fun `correct serialization of a consensual Signed Transaction`() {
        val serializationService = TestSerializationService.getTestSerializationService({
            it.register(partySerializer, it)
        }, schemeMetadata)

        val builder =
            checkpointSerializerBuilderFactory.createCheckpointSerializerBuilder(sandboxManagementService.group1)
        val kryoSerializer = builder
            .addSerializer(WireTransaction::class.java, wireTransactionKryoSerializer)
            .addSerializer(ConsensualSignedTransactionImpl::class.java, consensualSignedTransactionImplSeralizer)
            .build()

        val signedTransaction = getConsensualSignedTransactionImpl(
            digestService,
            merkleTreeFactory,
            serializationService,
            jsonMarshallingService
        )
        val bytes = kryoSerializer.serialize(signedTransaction)
        val deserialized = kryoSerializer.deserialize(bytes, ConsensualSignedTransactionImpl::class.java)

        assertThat(deserialized).isEqualTo(signedTransaction)
        Assertions.assertDoesNotThrow {
            deserialized.id
        }
        Assertions.assertEquals(signedTransaction.id, deserialized.id)
    }
}

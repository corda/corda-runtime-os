package net.corda.ledger.common.integration.test

import net.corda.common.json.validation.JsonValidator
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.flow.pipeline.sandbox.impl.FlowSandboxGroupContextImpl
import net.corda.internal.serialization.AMQP_STORAGE_CONTEXT
import net.corda.internal.serialization.AMQP_STORAGE_CONTEXT_NO_COMPRESSION
import net.corda.internal.serialization.SerializationServiceImpl
import net.corda.internal.serialization.amqp.helper.createSerializerFactory
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.common.testkit.createExample
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.sandboxgroupcontext.getSandboxSingletonService
import net.corda.serialization.checkpoint.CheckpointSerializer
import net.corda.testing.sandboxes.CpiLoader
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.AllTestsLifecycle
import net.corda.testing.sandboxes.testkit.VirtualNodeService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import org.slf4j.LoggerFactory
import java.nio.file.Path

const val TIMEOUT_MILLIS = 30000L

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class CommonLedgerIntegrationTest {
    @RegisterExtension
    val lifecycle = AllTestsLifecycle()

    private val logger = LoggerFactory.getLogger(this::class.java)

    open val testingCpb = "/META-INF/ledger-common-empty-app.cpb"

    private lateinit var flowSandboxService: FlowSandboxService
    private val _sandboxGroupContext = mutableListOf<SandboxGroupContext>()
    val sandboxGroupContext: SandboxGroupContext
        get() = _sandboxGroupContext.single()
    lateinit var jsonMarshallingService: JsonMarshallingService
    lateinit var jsonValidator: JsonValidator
    lateinit var wireTransactionFactory: WireTransactionFactory
    lateinit var wireTransaction: WireTransaction
    lateinit var kryoSerializer: CheckpointSerializer
    lateinit var serializationService: SerializationService

    @InjectService(timeout = TIMEOUT_MILLIS)
    lateinit var currentSandboxGroupContext: CurrentSandboxGroupContext

    @BeforeAll
    fun setup(
        @InjectService(timeout = TIMEOUT_MILLIS)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        baseDirectory: Path
    ) {
        sandboxSetup.configure(bundleContext, baseDirectory)
        lifecycle.accept(sandboxSetup, ::initialize)
    }

    open fun initialize(setup: SandboxSetup) {
        // Load VirtualNodeService before FlowSandboxService so that
        // FlowSandboxService can use the correct VirtualNodeInfoReadService.
        val virtualNode = setup.fetchService<VirtualNodeService>(TIMEOUT_MILLIS)
        val virtualNodeInfo = virtualNode.loadVirtualNode(testingCpb)
        logger.info("Created virtual node with ID ${virtualNodeInfo.holdingIdentity.shortHash}")
        logger.info("Reading metadata for CPI ${virtualNodeInfo.cpiIdentifier}")
        val cpiLoader = setup.fetchService<CpiLoader>(TIMEOUT_MILLIS)
        val cpiMetadata = cpiLoader.getCpiMetadata(virtualNodeInfo.cpiIdentifier).get()
            ?: fail("CpiMetadata is null ${virtualNodeInfo.cpiIdentifier}")
        val cpks = cpiMetadata.cpksMetadata.mapTo(linkedSetOf(), CpkMetadata::fileChecksum)

        flowSandboxService = setup.fetchService(TIMEOUT_MILLIS)
        _sandboxGroupContext += flowSandboxService.get(virtualNodeInfo.holdingIdentity, cpks)
        setup.withCleanup {
            val virtualNodeContext = sandboxGroupContext.virtualNodeContext
            _sandboxGroupContext.clear()
            virtualNode.releaseVirtualNode(virtualNodeContext)
        }
        currentSandboxGroupContext.set(sandboxGroupContext)

        jsonMarshallingService = sandboxGroupContext.getSandboxSingletonService()
        jsonValidator = sandboxGroupContext.getSandboxSingletonService()
        wireTransactionFactory = sandboxGroupContext.getSandboxSingletonService()
        kryoSerializer = sandboxGroupContext.getObjectByKey(FlowSandboxGroupContextImpl.CHECKPOINT_SERIALIZER)
            ?: fail("No CheckpointSerializer in sandbox context")
        serializationService = SerializationServiceImpl(
            // Use different SerializerFactories for serializationOutput and deserializationInput to not let them share
            // anything unintentionally
            outputFactory = sandboxGroupContext.createSerializerFactory(),
            inputFactory = sandboxGroupContext.createSerializerFactory(),
            contextWithCompression = AMQP_STORAGE_CONTEXT.withSandboxGroup(sandboxGroupContext.sandboxGroup),
            contextWithoutCompression = AMQP_STORAGE_CONTEXT_NO_COMPRESSION.withSandboxGroup(sandboxGroupContext.sandboxGroup)
        )

        wireTransaction = wireTransactionFactory.createExample(jsonMarshallingService, jsonValidator)

    }

    @AfterAll
    fun afterAll() {
        currentSandboxGroupContext.remove()
    }
}

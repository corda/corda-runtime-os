package net.corda.flow.pipeline.runner

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.crypto.core.parseSecureHash
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.session.SessionCounterpartyInfoRequest
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.data.flow.state.checkpoint.FlowStackItemSession
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.flow.state.waiting.start.WaitingForStartFlow
import net.corda.data.flow.state.waiting.external.ExternalEventResponse
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.BOB_X500_HOLDING_IDENTITY
import net.corda.flow.FLOW_ID_1
import net.corda.flow.SESSION_ID_1
import net.corda.flow.fiber.ClientStartedFlow
import net.corda.flow.fiber.FiberFuture
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.InitiatedFlow
import net.corda.flow.fiber.factory.FlowFiberFactory
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.factory.FlowFactory
import net.corda.flow.pipeline.factory.FlowFiberExecutionContextFactory
import net.corda.flow.pipeline.runner.impl.FlowRunnerImpl
import net.corda.flow.pipeline.runner.impl.remoteToLocalContextMapper
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.state.FlowStack
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.flow.utils.KeyValueStore
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.sandboxgroupcontext.service.SandboxDependencyInjector
import net.corda.session.manager.Constants.Companion.FLOW_SESSION_REQUIRE_CLOSE
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.OperationalStatus
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID

class FlowRunnerImplTest {

    private val flowFiberFactory = mock<FlowFiberFactory>()
    private val flowFactory = mock<FlowFactory>()
    private val flowStack = mock<FlowStack>()
    private val flowCheckpoint = mock<FlowCheckpoint>()
    private val sandboxGroupContext = mock<FlowSandboxGroupContext>()
    private val flowFiberExecutionContextFactory = mock<FlowFiberExecutionContextFactory>()
    private val cpiInfoReadService = mock<CpiInfoReadService>()
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService>()
    private val sandboxDependencyInjector = mock<SandboxDependencyInjector<Flow>>()
    private val fiberFuture = mock<FiberFuture>()
    private val platformInfoProvider = mock<PlatformInfoProvider> { on { localWorkerPlatformVersion} doReturn 67890 }
    private var flowFiberExecutionContext: FlowFiberExecutionContext
    private var flowStackItem = FlowStackItem().apply { sessions = mutableListOf() }
    private var clientFlow = mock<ClientStartableFlow>()
    private var initiatedFlow = mock<ResponderFlow>()

    private val flowRunner = FlowRunnerImpl(
        flowFiberFactory,
        flowFactory,
        flowFiberExecutionContextFactory,
        cpiInfoReadService,
        virtualNodeInfoReadService,
        platformInfoProvider
    )

    private val userContext = KeyValueStore().apply {
        this["user"] = "user"
    }
    private val platformContext = KeyValueStore().apply {
        this["platform"] = "platform"
    }
    private val sessionContext = KeyValueStore().apply {
        this[FLOW_SESSION_REQUIRE_CLOSE] = "true"
    }

    init {
        whenever(flowCheckpoint.flowId).thenReturn(FLOW_ID_1)
        whenever(flowCheckpoint.flowStack).thenReturn(flowStack)
        whenever(sandboxGroupContext.dependencyInjector).thenReturn(sandboxDependencyInjector)
        flowFiberExecutionContext = FlowFiberExecutionContext(
            flowCheckpoint,
            sandboxGroupContext,
            BOB_X500_HOLDING_IDENTITY.toCorda(),
            mock(),
            mock(),
            emptyMap(),
            mock(),
            emptyMap(),
            mock()
        )
        whenever(virtualNodeInfoReadService.get(any())).thenReturn(getMockVNodeInfo())
        whenever(cpiInfoReadService.get(any())).thenReturn(getMockCpiMetaData())
        whenever(flowCheckpoint.initialPlatformVersion).thenReturn(67890)
        whenever(platformInfoProvider.localWorkerSoftwareVersion).thenReturn("67890")
        whenever(flowCheckpoint.waitingFor).thenReturn(WaitingFor(WaitingForStartFlow()))
    }

    @BeforeEach
    fun setup() {
        whenever(flowFiberExecutionContextFactory.createFiberExecutionContext(any())).thenReturn(
            flowFiberExecutionContext
        )
    }

    @Test
    fun `start flow event should create a new flow and execute it in a new fiber`() {
        val startArgs = "args"
        val flowContinuation = FlowContinuation.Run()
        val flowStartContext = FlowStartContext().apply {
            contextPlatformProperties = platformContext.avro
        }
        val flowStartEvent = StartFlow().apply {
            startContext = flowStartContext
            flowStartArgs = startArgs
        }
        val clientRequestBody = mock<ClientRequestBody>()
        whenever(clientRequestBody.requestBody).thenReturn(startArgs)
        val logicAndArgs = ClientStartedFlow(clientFlow, clientRequestBody)

        val context = buildFlowEventContext<Any>(flowCheckpoint, flowStartEvent)
        whenever(flowFactory.createFlow(flowStartEvent, sandboxGroupContext)).thenReturn(logicAndArgs)
        whenever(
            flowFiberFactory.createAndStartFlowFiber(
                eq(flowFiberExecutionContext),
                eq(FLOW_ID_1),
                eq(logicAndArgs)
            )
        ).thenReturn(fiberFuture)

        whenever(
            flowStack.pushWithContext(
                eq(clientFlow),
                eq(emptyKeyValuePairList()),
                eq(platformContext.avro),
                any()
            )
        ).thenReturn(
            flowStackItem
        )

        val result = flowRunner.runFlow(context, flowContinuation)

        assertThat(result).isSameAs(fiberFuture)
        verify(sandboxDependencyInjector).injectServices(clientFlow)
    }

    @Test
    fun `start flow event does not create new flow if waiting for is not set to correct value`() {
        val startArgs = "args"
        val flowContinuation = FlowContinuation.Run()
        val flowStartContext = FlowStartContext().apply {
            contextPlatformProperties = platformContext.avro
        }
        val flowStartEvent = StartFlow().apply {
            startContext = flowStartContext
            flowStartArgs = startArgs
        }

        whenever(flowCheckpoint.waitingFor).thenReturn(WaitingFor(ExternalEventResponse("foo")))
        val context = buildFlowEventContext<Any>(flowCheckpoint, flowStartEvent)

        whenever(flowFiberFactory.createAndResumeFlowFiber(flowFiberExecutionContext, flowContinuation)).thenReturn(
            fiberFuture
        )

        val result = flowRunner.runFlow(context, flowContinuation)

        assertThat(result).isSameAs(fiberFuture)
        verify(sandboxDependencyInjector, times(0)).injectServices(clientFlow)
    }

    @Test
    fun `Counterparty request flow session event should create a new flow and execute it in a new fiber`() {
        val sessionInit = SessionInit().apply {
            contextPlatformProperties = platformContext.avro
            contextUserProperties = userContext.avro
        }

        runInitiatedTest(SessionCounterpartyInfoRequest(sessionInit))
    }

    @Test
    fun `First SessionData with Init Info should create a new flow and execute it in a new fiber`() {
        val sessionInitPayload = SessionInit().apply {
            contextPlatformProperties = platformContext.avro
            contextUserProperties = userContext.avro
        }

        val sessionData = SessionData().apply {
            payload = ByteBuffer.allocate(1)
            sessionInit = sessionInitPayload
        }
        runInitiatedTest(sessionData)
    }

    private fun runInitiatedTest(eventPayload: Any) {
        val sessionEvent = SessionEvent().apply {
            sessionId = SESSION_ID_1
            sequenceNum = 1
            payload = eventPayload
            contextSessionProperties = sessionContext.avro
        }

        val flowContinuation = FlowContinuation.Run()

        val flowStartContext = FlowStartContext().apply {
            statusKey = FlowKey().apply {
                id = SESSION_ID_1
                identity = BOB_X500_HOLDING_IDENTITY
            }
            initiatedBy = HoldingIdentity().apply {
                x500Name = MemberX500Name("R3", "London", "GB").toString()
            }
        }
        val logicAndArgs = InitiatedFlow(initiatedFlow, mock())

        // Map the mock context properties to local context properties in the same way the flow runner should, the exact
        // content of the mapped local context is out of the scope of this test
        val localContextProperties = remoteToLocalContextMapper(
            remoteUserContextProperties = userContext.avro,
            remotePlatformContextProperties = platformContext.avro,
            mapOf("corda.account" to "account-zero")
        )

        val context = buildFlowEventContext<Any>(flowCheckpoint, sessionEvent)
        whenever(flowCheckpoint.flowStartContext).thenReturn(flowStartContext)
        whenever(
            flowFactory.createInitiatedFlow(
                flowStartContext,
                true,
                sessionTimeout = null,
                sandboxGroupContext,
                localContextProperties.sessionProperties
            )
        ).thenReturn(logicAndArgs)
        whenever(
            flowFiberFactory.createAndStartFlowFiber(
                eq(flowFiberExecutionContext),
                eq(FLOW_ID_1),
                eq(logicAndArgs)
            )
        ).thenReturn(fiberFuture)

        whenever(
            flowStack.pushWithContext(
                eq(initiatedFlow),
                eq(localContextProperties.userProperties),
                eq(localContextProperties.platformProperties),
                any()
            )
        ).thenReturn(
            flowStackItem
        )

        val result = flowRunner.runFlow(context, flowContinuation)

        assertThat(result).isSameAs(fiberFuture)

        assertThat(flowStackItem.sessions).containsOnly(FlowStackItemSession(SESSION_ID_1, true))
        verify(sandboxDependencyInjector).injectServices(initiatedFlow)
    }

    @Test
    fun `session init does not start a new flow if correct waiting for is not set`() {
        val flowContinuation = FlowContinuation.Run()
        val sessionInit = SessionInit().apply {
            contextPlatformProperties = platformContext.avro
            contextUserProperties = userContext.avro
        }
        val sessionEvent = SessionEvent().apply {
            payload = sessionInit
        }
        val context = buildFlowEventContext<Any>(flowCheckpoint, sessionEvent)
        whenever(flowCheckpoint.waitingFor).thenReturn(WaitingFor(ExternalEventResponse("foo")))

        whenever(flowFiberFactory.createAndResumeFlowFiber(flowFiberExecutionContext, flowContinuation)).thenReturn(
            fiberFuture
        )

        val result = flowRunner.runFlow(context, flowContinuation)

        assertThat(result).isSameAs(fiberFuture)
    }

    @Test
    fun `other event types resume existing flow`() {
        val flowContinuation = FlowContinuation.Run()
        val context = buildFlowEventContext<Any>(flowCheckpoint, net.corda.data.flow.event.external.ExternalEventResponse())

        whenever(flowFiberFactory.createAndResumeFlowFiber(flowFiberExecutionContext, flowContinuation)).thenReturn(
            fiberFuture
        )

        val result = flowRunner.runFlow(context, flowContinuation)

        assertThat(result).isSameAs(fiberFuture)
    }

    @Test
    fun `Second SessionData with Init Info should resume existing flow`() {
        whenever(flowCheckpoint.waitingFor).thenReturn(WaitingFor(net.corda.data.flow.state.waiting.SessionData()))
        val sessionInitPayload = SessionInit().apply {
            contextPlatformProperties = platformContext.avro
            contextUserProperties = userContext.avro
        }

        val sessionData = SessionData().apply {
            payload = ByteBuffer.allocate(1)
            sessionInit = sessionInitPayload
        }

        val sessionEvent = SessionEvent().apply {
            sequenceNum = 2
            payload = sessionData
            contextSessionProperties = sessionContext.avro
        }

        val flowContinuation = FlowContinuation.Run()
        val context = buildFlowEventContext<Any>(flowCheckpoint, sessionEvent)

        whenever(flowFiberFactory.createAndResumeFlowFiber(flowFiberExecutionContext, flowContinuation)).thenReturn(
            fiberFuture
        )

        val result = flowRunner.runFlow(context, flowContinuation)

        assertThat(result).isSameAs(fiberFuture)
    }

    @Test
    fun `resuming a flow fails when the platform version is different`() {
        val flowContinuation = FlowContinuation.Run()
        val context = buildFlowEventContext<Any>(flowCheckpoint, net.corda.data.flow.event.external.ExternalEventResponse())

        whenever(flowCheckpoint.initialPlatformVersion).thenReturn(500100)

        assertThatExceptionOfType(FlowFatalException::class.java).isThrownBy {
            flowRunner.runFlow(context, flowContinuation)
        }
    }

    private fun getMockVNodeInfo(): VirtualNodeInfo {
        return VirtualNodeInfo(
            BOB_X500_HOLDING_IDENTITY.toCorda(),
            getMockCpiId(),
            null,
            UUID.randomUUID(),
            null,
            UUID.randomUUID(),
            null,
            UUID.randomUUID(),
            null,
            OperationalStatus.ACTIVE,
            OperationalStatus.ACTIVE,
            OperationalStatus.ACTIVE,
            OperationalStatus.ACTIVE,
            null,
            null,
            1,
            Instant.now()
        )
    }

    private fun getMockCpiId(): CpiIdentifier {
        return CpiIdentifier("CpiName", "1", parseSecureHash("ALGO:0987654321"))
    }

    private fun getMockCpiMetaData(): CpiMetadata {
        return CpiMetadata(
            getMockCpiId(),
            parseSecureHash("ALGO:0987654321"),
            mock(),
            null,
            2,
            Instant.now(),
        )
    }
}
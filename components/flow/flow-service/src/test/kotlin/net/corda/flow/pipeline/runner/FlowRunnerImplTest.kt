package net.corda.flow.pipeline.runner

import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.data.flow.state.checkpoint.FlowStackItemSession
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.BOB_X500_HOLDING_IDENTITY
import net.corda.flow.FLOW_ID_1
import net.corda.flow.SESSION_ID_1
import net.corda.flow.fiber.FiberFuture
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.InitiatedFlow
import net.corda.flow.fiber.ClientStartedFlow
import net.corda.flow.fiber.factory.FlowFiberFactory
import net.corda.flow.pipeline.factory.FlowFactory
import net.corda.flow.pipeline.factory.FlowFiberExecutionContextFactory
import net.corda.flow.pipeline.runner.impl.FlowRunnerImpl
import net.corda.flow.pipeline.runner.impl.remoteToLocalContextMapper
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.flow.pipeline.sandbox.SandboxDependencyInjector
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.state.FlowStack
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.flow.utils.KeyValueStore
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlowRunnerImplTest {

    private val flowFiberFactory = mock<FlowFiberFactory>()
    private val flowFactory = mock<FlowFactory>()
    private val flowStack = mock<FlowStack>()
    private val flowCheckpoint = mock<FlowCheckpoint>()
    private val sandboxGroupContext = mock<FlowSandboxGroupContext>()
    private val flowFiberExecutionContextFactory = mock<FlowFiberExecutionContextFactory>()
    private val sandboxDependencyInjector = mock<SandboxDependencyInjector>()
    private val fiberFuture = mock<FiberFuture>()
    private var flowFiberExecutionContext: FlowFiberExecutionContext
    private var flowStackItem = FlowStackItem().apply { sessions = mutableListOf() }
    private var clientFlow = mock<ClientStartableFlow>()
    private var initiatedFlow = mock<ResponderFlow>()

    private val flowRunner = FlowRunnerImpl(flowFiberFactory, flowFactory, flowFiberExecutionContextFactory)

    private val userContext = KeyValueStore().apply {
        this["user"] = "user"
    }
    private val platformContext = KeyValueStore().apply {
        this["platform"] = "platform"
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
            emptyMap()
        )
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
        whenever(clientRequestBody.getRequestBody()).thenReturn(startArgs)
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

        whenever(flowStack.pushWithContext(clientFlow, emptyKeyValuePairList(), platformContext.avro)).thenReturn(
            flowStackItem
        )

        val result = flowRunner.runFlow(context, flowContinuation)

        assertThat(result).isSameAs(fiberFuture)

        verify(sandboxDependencyInjector).injectServices(clientFlow)
    }

    @Test
    fun `initiate flow session event should create a new flow and execute it in a new fiber`() {
        val flowContinuation = FlowContinuation.Run()
        val sessionInit = SessionInit().apply {
            contextPlatformProperties = platformContext.avro
            contextUserProperties = userContext.avro
        }

        val flowStartContext = FlowStartContext().apply {
            statusKey = FlowKey().apply {
                id = SESSION_ID_1
                identity = BOB_X500_HOLDING_IDENTITY
            }
            initiatedBy = HoldingIdentity().apply {
                x500Name = MemberX500Name("R3", "London", "GB").toString()
            }
        }
        val sessionEvent = SessionEvent().apply {
            payload = sessionInit
        }
        val logicAndArgs = InitiatedFlow(initiatedFlow, mock())

        // Map the mock context properties to local context properties in the same way the flow runner should, the exact
        // content of the mapped local context is out of the scope of this test
        val localContextProperties = remoteToLocalContextMapper(
            remoteUserContextProperties = userContext.avro,
            remotePlatformContextProperties = platformContext.avro
        )

        val context = buildFlowEventContext<Any>(flowCheckpoint, sessionEvent)
        whenever(flowCheckpoint.flowStartContext).thenReturn(flowStartContext)
        whenever(
            flowFactory.createInitiatedFlow(
                flowStartContext,
                sandboxGroupContext,
                localContextProperties.counterpartySessionProperties
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
                initiatedFlow,
                localContextProperties.userProperties,
                localContextProperties.platformProperties
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
    fun `other event types resume existing flow`() {
        val flowContinuation = FlowContinuation.Run()
        val context = buildFlowEventContext<Any>(flowCheckpoint, Wakeup())

        whenever(flowFiberFactory.createAndResumeFlowFiber(flowFiberExecutionContext, flowContinuation)).thenReturn(
            fiberFuture
        )

        val result = flowRunner.runFlow(context, flowContinuation)

        assertThat(result).isSameAs(fiberFuture)
    }
}
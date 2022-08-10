package net.corda.flow.pipeline.runner

import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.BOB_X500_HOLDING_IDENTITY
import net.corda.flow.FLOW_ID_1
import net.corda.flow.SESSION_ID_1
import net.corda.flow.fiber.FiberFuture
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.InitiatedFlow
import net.corda.flow.fiber.RPCStartedFlow
import net.corda.flow.fiber.factory.FlowFiberFactory
import net.corda.flow.pipeline.factory.FlowFactory
import net.corda.flow.pipeline.factory.FlowFiberExecutionContextFactory
import net.corda.flow.pipeline.runner.impl.FlowRunnerImpl
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.flow.pipeline.sandbox.SandboxDependencyInjector
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.state.FlowStack
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
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
    private val fiber = mock<FlowFiber>()
    private val fiberFuture = mock<FiberFuture>()
    private var flowFiberExecutionContext: FlowFiberExecutionContext
    private var flowStackItem = FlowStackItem().apply { sessionIds = mutableListOf() }
    private var rpcFlow = mock<RPCStartableFlow>()
    private var initiatedFlow = mock<ResponderFlow>()

    private val flowRunner = FlowRunnerImpl(flowFiberFactory, flowFactory, flowFiberExecutionContextFactory)

    init {
        whenever(flowCheckpoint.flowId).thenReturn(FLOW_ID_1)
        whenever(flowCheckpoint.flowStack).thenReturn(flowStack)
        whenever(sandboxGroupContext.dependencyInjector).thenReturn(sandboxDependencyInjector)
        flowFiberExecutionContext = FlowFiberExecutionContext(
            flowCheckpoint,
            sandboxGroupContext,
            BOB_X500_HOLDING_IDENTITY.toCorda(),
            mock()
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
        val flowStartContext = FlowStartContext()
        val flowStartEvent = StartFlow().apply {
            startContext = flowStartContext
            flowStartArgs = startArgs
        }
        val rpcRequestData = mock<RPCRequestData>()
        whenever(rpcRequestData.getRequestBody()).thenReturn(startArgs)
        val logicAndArgs = RPCStartedFlow(rpcFlow, rpcRequestData)

        val context = buildFlowEventContext<Any>(flowCheckpoint, flowStartEvent)
        whenever(flowFactory.createFlow(flowStartEvent, sandboxGroupContext)).thenReturn(logicAndArgs)
        whenever(
            flowFiberFactory.createAndStartFlowFiber(
                eq(flowFiberExecutionContext),
                eq(FLOW_ID_1),
                eq(logicAndArgs)
            )
        ).thenReturn(fiberFuture)
        whenever(flowStack.push(rpcFlow)).thenReturn(flowStackItem)

        val result = flowRunner.runFlow(context, flowContinuation)

        assertThat(result).isSameAs(fiberFuture)

        verify(sandboxDependencyInjector).injectServices(rpcFlow)
    }

    @Test
    fun `initiate flow session event should create a new flow and execute it in a new fiber`() {
        val userKeyValuePairList = emptyKeyValuePairList()
        val platformKeyValuePairList = emptyKeyValuePairList()

        val flowContinuation = FlowContinuation.Run()
        val sessionInit = SessionInit().apply {
            setContextPlatformProperties(platformKeyValuePairList)
            setContextUserProperties(userKeyValuePairList)
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

        val context = buildFlowEventContext<Any>(flowCheckpoint, sessionEvent)
        whenever(flowCheckpoint.flowStartContext).thenReturn(flowStartContext)
        whenever(flowFactory.createInitiatedFlow(flowStartContext, sandboxGroupContext)).thenReturn(logicAndArgs)
        whenever(
            flowFiberFactory.createAndStartFlowFiber(
                eq(flowFiberExecutionContext),
                eq(FLOW_ID_1),
                eq(logicAndArgs)
            )
        ).thenReturn(fiberFuture)

        whenever(flowStack.pushWithContext(initiatedFlow, any(), any())).thenReturn(flowStackItem)

        val result = flowRunner.runFlow(context, flowContinuation)

        assertThat(result).isSameAs(fiberFuture)

        assertThat(flowStackItem.sessionIds).containsOnly(SESSION_ID_1)
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
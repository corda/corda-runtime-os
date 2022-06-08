package net.corda.flow.pipeline.factory

import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.StartFlow
import net.corda.flow.BOB_X500_HOLDING_IDENTITY
import net.corda.flow.BOB_X500_NAME
import net.corda.flow.SESSION_ID_1
import net.corda.flow.application.sessions.factory.FlowSessionFactory
import net.corda.flow.fiber.InitiatedFlow
import net.corda.flow.fiber.RPCStartedFlow
import net.corda.flow.pipeline.factory.impl.FlowFactoryImpl
import net.corda.sandbox.SandboxGroup
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FlowFactoryImplTest {

    private val className = "flow-class-1"
    private val flowSession = mock<FlowSession>()
    private val sandboxGroupContext = mock<SandboxGroupContext>()
    private val sandboxGroup = mock<SandboxGroup>()
    private val flowSessionFactory = mock<FlowSessionFactory>()
    private val flowFactory = FlowFactoryImpl(flowSessionFactory)

    @BeforeEach
    fun setup() {
        whenever(sandboxGroupContext.sandboxGroup).thenReturn(sandboxGroup)
    }

    @Test
    fun `create initiated flow`() {
        val flowStartContext = FlowStartContext().apply {
            statusKey = FlowKey(SESSION_ID_1, BOB_X500_HOLDING_IDENTITY)
            initiatedBy = BOB_X500_HOLDING_IDENTITY
            flowClassName = className
        }

        whenever(flowSessionFactory.create(SESSION_ID_1, BOB_X500_NAME, true)).thenReturn(flowSession)
        whenever(sandboxGroup.loadClassFromMainBundles(className, ResponderFlow::class.java))
            .thenReturn(ExampleFlow2::class.java)

        val result = flowFactory.createInitiatedFlow(flowStartContext, sandboxGroupContext) as InitiatedFlow
        assertTrue(result.logic is ExampleFlow2)
    }

    @Test
    fun `create flow`() {
        val startArgs = "args"
        val flowStartContext = FlowStartContext().apply {
            statusKey = FlowKey(SESSION_ID_1, BOB_X500_HOLDING_IDENTITY)
            initiatedBy = BOB_X500_HOLDING_IDENTITY
            flowClassName = className
        }
        val flowStartEvent = StartFlow(). apply {
            startContext = flowStartContext
            flowStartArgs= startArgs
        }

        whenever(sandboxGroup.loadClassFromMainBundles(className, RPCStartableFlow::class.java))
            .thenReturn(ExampleFlow1::class.java)

        val result = flowFactory.createFlow(flowStartEvent, sandboxGroupContext) as RPCStartedFlow
        assertTrue(result.logic is ExampleFlow1)
        assertEquals("result", result.invoke())
    }

    class ExampleFlow1 : RPCStartableFlow {
        override fun call(requestBody: String) : String {
            return "result"
        }
    }

    class ExampleFlow2 : ResponderFlow {
        override fun call(session: FlowSession) {
        }
    }
}
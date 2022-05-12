package net.corda.flow.pipeline.factory

import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.StartFlow
import net.corda.flow.BOB_X500_HOLDING_IDENTITY
import net.corda.flow.BOB_X500_NAME
import net.corda.flow.SESSION_ID_1
import net.corda.flow.application.sessions.factory.FlowSessionFactory
import net.corda.flow.pipeline.factory.impl.FlowFactoryImpl
import net.corda.sandbox.SandboxGroup
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.messaging.FlowSession
import org.assertj.core.api.Assertions.assertThat
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
        whenever(sandboxGroup.loadClassFromMainBundles(className, Flow::class.java))
            .thenReturn(ExampleFlow2::class.java)

        val result = flowFactory.createInitiatedFlow(flowStartContext, sandboxGroupContext) as ExampleFlow2
        assertThat(result.flowSession).isSameAs(flowSession)
    }

    @Test
    fun `create flow`() {
        var startArgs = "args"
        val flowStartContext = FlowStartContext().apply {
            statusKey = FlowKey(SESSION_ID_1, BOB_X500_HOLDING_IDENTITY)
            initiatedBy = BOB_X500_HOLDING_IDENTITY
            flowClassName = className
        }
        val flowStartEvent = StartFlow(). apply {
            startContext = flowStartContext
            flowStartArgs= startArgs
        }

        whenever(sandboxGroup.loadClassFromMainBundles(className, Flow::class.java))
            .thenReturn(ExampleFlow1::class.java)

        val result = flowFactory.createFlow(flowStartEvent, sandboxGroupContext) as ExampleFlow1
        assertThat(result.args).isEqualTo(startArgs)
    }

    class ExampleFlow1(val args: String) : Flow<Unit> {
        override fun call() {
        }
    }

    class ExampleFlow2(val flowSession: FlowSession) : Flow<Unit> {
        override fun call() {
        }
    }
}
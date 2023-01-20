package net.corda.flow.pipeline.factory

import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.StartFlow
import net.corda.flow.BOB_X500_HOLDING_IDENTITY
import net.corda.flow.BOB_X500_NAME
import net.corda.flow.SESSION_ID_1
import net.corda.flow.application.sessions.factory.FlowSessionFactory
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.InitiatedFlow
import net.corda.flow.fiber.ClientStartedFlow
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.factory.impl.FlowFactoryImpl
import net.corda.flow.pipeline.factory.sample.flows.ExampleJavaFlow
import net.corda.flow.pipeline.factory.sample.flows.NoDefaultConstructorJavaFlow
import net.corda.flow.pipeline.factory.sample.flows.PrivateConstructorJavaFlow
import net.corda.sandbox.SandboxGroup
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FlowFactoryImplTest {
    private val flowSession = mock<FlowSession>()
    private val sandboxGroupContext = mock<SandboxGroupContext>()
    private val sandboxGroup = mock<SandboxGroup>()
    private val flowSessionFactory = mock<FlowSessionFactory>()
    private val flowFiberService = mock<FlowFiberService>()
    private val flowFactory = FlowFactoryImpl(flowSessionFactory, flowFiberService)
    private val contextMap = mapOf("key" to "value")
    private val flowStartContext = FlowStartContext().apply {
        startArgs = "flow-argument-1"
        statusKey = FlowKey(SESSION_ID_1, BOB_X500_HOLDING_IDENTITY)
        initiatedBy = BOB_X500_HOLDING_IDENTITY
    }

    @BeforeEach
    fun setup() {
        whenever(sandboxGroupContext.sandboxGroup).thenReturn(sandboxGroup)
    }

    @Test
    fun `create flow throws flow fatal exception on error`() {
        val flowStartEvent = StartFlow().apply {
            startContext = flowStartContext
            flowStartArgs = "flow-argument-2"
        }
        whenever(sandboxGroup.loadClassFromMainBundles("com.MyClassName", ClientStartableFlow::class.java))
            .thenThrow(IllegalStateException())

        assertThatThrownBy {
            flowFactory.createFlow(flowStartEvent, sandboxGroupContext)
        }.isInstanceOf(FlowFatalException::class.java)
    }

    @Test
    fun `create initiated flow throws flow fatal exception on error`() {
        whenever(flowSessionFactory.createInitiatedFlowSession(SESSION_ID_1, BOB_X500_NAME, contextMap))
            .thenReturn(flowSession)
        whenever(sandboxGroup.loadClassFromMainBundles("com.MyClassName", ResponderFlow::class.java))
            .thenThrow(IllegalStateException())

        assertThatThrownBy {
            flowFactory.createInitiatedFlow(flowStartContext, sandboxGroupContext, contextMap)
        }.isInstanceOf(FlowFatalException::class.java)
    }

    @ParameterizedTest
    @ValueSource(classes = [NoDefaultConstructorJavaFlow::class, NoDefaultConstructorFlow::class])
    fun `create flow throws fatal exception when flow class does not have default no args constructor`(flowClass: Class<*>) {
        val testFlowClassName = flowClass.name
        flowStartContext.flowClassName = testFlowClassName
        doReturn(flowClass).whenever(sandboxGroup)
            .loadClassFromMainBundles(testFlowClassName, ClientStartableFlow::class.java)

        assertThatThrownBy {
            flowFactory.createFlow(
                StartFlow().apply { startContext = flowStartContext },
                sandboxGroupContext
            )
        }
            .hasMessageContaining(testFlowClassName)
            .isInstanceOf(FlowFatalException::class.java)
            .hasCauseInstanceOf(NoSuchMethodException::class.java)
    }

    @ParameterizedTest
    @ValueSource(classes = [PrivateConstructorJavaFlow::class, PrivateConstructorFlow::class])
    fun `create flow throws fatal exception when flow class does not have default public constructor`(flowClass: Class<*>) {
        val testFlowClassName = flowClass.name
        flowStartContext.flowClassName = testFlowClassName
        doReturn(flowClass).whenever(sandboxGroup)
            .loadClassFromMainBundles(testFlowClassName, ClientStartableFlow::class.java)

        assertThatThrownBy {
            flowFactory.createFlow(
                StartFlow().apply { startContext = flowStartContext },
                sandboxGroupContext
            )
        }
            .hasMessageContaining(testFlowClassName)
            .isInstanceOf(FlowFatalException::class.java)
            .hasCauseInstanceOf(IllegalAccessException::class.java)
    }

    @ParameterizedTest
    @ValueSource(classes = [NoDefaultConstructorJavaFlow::class, NoDefaultConstructorFlow::class])
    fun `create initiated flow throws fatal exception when flow class does not have default no arg constructor`(
        flowClass: Class<*>
    ) {
        val testFlowClassName = flowClass.name
        flowStartContext.flowClassName = testFlowClassName
        doReturn(flowClass).whenever(sandboxGroup)
            .loadClassFromMainBundles(testFlowClassName, ResponderFlow::class.java)

        assertThatThrownBy { flowFactory.createInitiatedFlow(flowStartContext, sandboxGroupContext, contextMap) }
            .hasMessageContaining(testFlowClassName)
            .isInstanceOf(FlowFatalException::class.java)
            .hasCauseInstanceOf(NoSuchMethodException::class.java)
    }

    @ParameterizedTest
    @ValueSource(classes = [PrivateConstructorJavaFlow::class, PrivateConstructorFlow::class])
    fun `create initiated flow throws fatal exception when flow class does not have default public constructor`(
        flowClass: Class<*>
    ) {
        val testFlowClassName = flowClass.name
        flowStartContext.flowClassName = testFlowClassName
        doReturn(flowClass).whenever(sandboxGroup)
            .loadClassFromMainBundles(testFlowClassName, ResponderFlow::class.java)

        assertThatThrownBy { flowFactory.createInitiatedFlow(flowStartContext, sandboxGroupContext, contextMap) }
            .hasMessageContaining(testFlowClassName)
            .isInstanceOf(FlowFatalException::class.java)
            .hasCauseInstanceOf(IllegalAccessException::class.java)
    }

    @ParameterizedTest
    @ValueSource(classes = [ExampleJavaFlow::class, ExampleFlow::class])
    fun `create flow`(flowClass: Class<*>) {
        val testFlowClassName = flowClass.name
        flowStartContext.flowClassName = testFlowClassName
        val flowStartEvent = StartFlow().apply {
            startContext = flowStartContext
            flowStartArgs = "flow-argument-2"
        }
        doReturn(flowClass).whenever(sandboxGroup)
            .loadClassFromMainBundles(testFlowClassName, ClientStartableFlow::class.java)

        val result = flowFactory.createFlow(flowStartEvent, sandboxGroupContext) as ClientStartedFlow
        assertThat(result.logic).isInstanceOf(flowClass)
        assertThat(result.invoke()).isEqualTo(flowClass.simpleName)
    }

    @ParameterizedTest
    @ValueSource(classes = [ExampleJavaFlow::class, ExampleFlow::class])
    fun `create initiated flow`(flowClass: Class<*>) {
        val testFlowClassName = flowClass.name
        flowStartContext.flowClassName = testFlowClassName
        whenever(flowSessionFactory.createInitiatedFlowSession(SESSION_ID_1, BOB_X500_NAME, contextMap)).thenReturn(
            flowSession
        )
        doReturn(flowClass).whenever(sandboxGroup)
            .loadClassFromMainBundles(testFlowClassName, ResponderFlow::class.java)

        val result = flowFactory.createInitiatedFlow(flowStartContext, sandboxGroupContext, contextMap) as InitiatedFlow
        assertThat(result.logic).isInstanceOf(flowClass)
    }

    class ExampleFlow : ClientStartableFlow, ResponderFlow {
        override fun call(session: FlowSession) {
        }

        override fun call(requestBody: RestRequestBody): String {
            return ExampleFlow::class.java.simpleName
        }
    }

    class PrivateConstructorFlow private constructor() : ClientStartableFlow, ResponderFlow {
        override fun call(session: FlowSession) {
            throw IllegalStateException("Should not reach this point")
        }

        @Suspendable
        override fun call(requestBody: RestRequestBody): String {
            throw IllegalStateException("Should not reach this point")
        }
    }

    class NoDefaultConstructorFlow(private val message: String) : ClientStartableFlow, ResponderFlow {
        override fun call(session: FlowSession) {
            throw IllegalStateException(message)
        }

        @Suspendable
        override fun call(requestBody: RestRequestBody): String {
            throw IllegalStateException(message)
        }
    }
}

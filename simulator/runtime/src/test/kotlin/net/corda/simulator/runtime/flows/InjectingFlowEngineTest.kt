package net.corda.simulator.runtime.flows

import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.runtime.messaging.SimFiber
import net.corda.simulator.runtime.messaging.SimFiberBase
import net.corda.simulator.runtime.messaging.SimFlowContextProperties
import net.corda.simulator.runtime.tools.CordaFlowChecker
import net.corda.simulator.tools.FlowChecker
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.base.types.MemberX500Name
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class InjectingFlowEngineTest {

    private val member = MemberX500Name.parse("CN=IRunCorDapps, OU=Application, O=R3, L=London, C=GB")

    @Test
    fun `should inject subflow with provided services then call it through the flow manager`() {
        // Given some services and an injector
        val fiber = mock<SimFiber>()
        val injector = mock<FlowServicesInjector>()
        val flowManager = mock<FlowManager>()
        val contextProperties = SimFlowContextProperties(emptyMap())

        // And a flow engine which uses them
        val engine = InjectingFlowEngine(mock(), member, fiber,contextProperties, injector, CordaFlowChecker(), flowManager)

        // When we pass a subflow to the flow engine
        val flow = mock<SubFlow<String>>()
        whenever(flowManager.call(flow)).thenReturn("Yo!")

        val response = engine.subFlow(flow)

        // Then it should inject those
        verify(injector, times(1))
            .injectServices(eq(flow), eq(member), eq(fiber), any())

        // And it should call the subFlow
        assertThat(response, `is`("Yo!"))
    }

    @Test
    fun `should run the flow checker on injected flows`() {
        // Given some services and an injector, and a flow checker which will throw an error
        val fiber = mock<SimFiber>()
        val contextProperties = SimFlowContextProperties(emptyMap())
        val injector = mock<FlowServicesInjector>()
        val flowChecker = mock<FlowChecker>()
        val configuration = mock<SimulatorConfiguration>()
        whenever(flowChecker.check(any())).thenThrow(IllegalArgumentException())

        // And a flow engine which uses them
        val engine = InjectingFlowEngine(configuration, member, fiber, contextProperties, injector, flowChecker)

        // When we pass a subflow to the flow engine
        val flow = mock<SubFlow<String>>()

        // Then it should throw an error
        assertThrows<java.lang.IllegalArgumentException> { engine.subFlow(flow) }
    }

    @Test
    fun `should return mutable flow context properties`(){
        // Given a SimFiber
        val fiber = SimFiberBase()
        val configuration = mock<SimulatorConfiguration>()
        val contextProperties = SimFlowContextProperties(emptyMap())

        //When a flow engine is created
        val engine = InjectingFlowEngine(configuration, member, fiber, contextProperties)

        // Then the context properties accessed via it should be mutable
        engine.flowContextProperties.put("new-key", "new-val")
        assertEquals("new-val", engine.flowContextProperties["new-key"])

    }
}
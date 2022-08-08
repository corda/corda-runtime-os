package net.corda.testutils.internal

import net.corda.testutils.tools.CordaFlowChecker
import net.corda.testutils.tools.FlowChecker
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.base.types.MemberX500Name
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class InjectingFlowEngineTest {

    private val x500 = MemberX500Name.parse("CN=IRunCorDapps, OU=Application, O=R3, L=London, C=GB")

    @Test
    fun `should inject subflow with provided services then call it`() {
        // Given some services and an injector
        val fiber = mock<FiberMock>()
        val injector = mock<FlowServicesInjector>()

        // And a flow engine which uses them
        val engine = InjectingFlowEngine(x500, fiber, injector, CordaFlowChecker())

        // When we pass a subflow to the flow engine
        val flow = mock<SubFlow<String>>()
        whenever(flow.call()).thenReturn("Yo!")

        val response = engine.subFlow(flow)

        // Then it should inject those
        verify(injector, times(1)).injectServices(eq(flow), eq(x500), eq(fiber), any())

        // And it should call the subFlow
        assertThat(response, `is`("Yo!"))
    }

    @Test
    fun `should run the flow checker on injected flows`() {
        // Given some services and an injector, and a flow checker which will throw an error
        val fiber = mock<FiberMock>()
        val injector = mock<FlowServicesInjector>()
        val flowChecker = mock<FlowChecker>()
        whenever(flowChecker.check(any())).thenThrow(IllegalArgumentException())

        // And a flow engine which uses them
        val engine = InjectingFlowEngine(x500, fiber, injector, flowChecker)

        // When we pass a subflow to the flow engine
        val flow = mock<SubFlow<String>>()

        // Then it should throw an error
        assertThrows<java.lang.IllegalArgumentException> { engine.subFlow(flow) }
    }
}
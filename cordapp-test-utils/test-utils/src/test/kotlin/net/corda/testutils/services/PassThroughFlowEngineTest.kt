package net.corda.testutils.services;

import net.corda.v5.application.flows.SubFlow
import net.corda.v5.base.types.MemberX500Name
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class PassThroughFlowEngineTest {

    private val member = MemberX500Name.parse("CN=IRunCorDapps, OU=Application, O=R3, L=London, C=GB")

    @Test
    fun `should call through to any subflows passed to the engine`() {
        // Given a subflow
        val subflow = mock<SubFlow<String>>()

        // When we pass it to the flow engine
        PassThroughFlowEngine(member).subFlow(subflow)

        // Then the flow engine should call it immediately
        verify(subflow, times(1)).call()
    }
}

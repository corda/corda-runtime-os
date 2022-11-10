package net.corda.simulator.runtime.utils

import net.corda.simulator.exceptions.NonImplementedAPIException
import net.corda.simulator.runtime.testflows.HelloFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock

class UtilitiesTest {

    @Test
    fun `should provide injector into flows`() {
        val flowEngine = mock<FlowEngine>()
        val flow = HelloFlow()
        flow.injectIfRequired(FlowEngine::class.java) { flowEngine }
        assertThat(flow.flowEngine, `is`(flowEngine))
    }

    @Test
    fun `should provide unique sandbox names based on member names`() {
        val member = MemberX500Name.parse("CN=IRunCordapps, OU=Application, O=R3, L=London, C=GB")
        assertThat(member.sandboxName,
            `is`("CN_IRunCordapps_OU_Application_O_R3_L_London_C_GB"))
    }


    @Test
    fun `should throw error when non supported service is injected to a flow`(){
        val flow1 = NonImplementedServiceFlow()
        assertThrows<NonImplementedAPIException>{
            checkAPIAvailability(flow1)
        }
        val flow2 = CustomServiceFlow()
        assertThrows<NonImplementedAPIException>{
            checkAPIAvailability(flow2)
        }

        val flow3 = HelloFlow()
        assertDoesNotThrow {
            checkAPIAvailability(flow3)
        }
    }

    class NonImplementedServiceFlow : ResponderFlow {
        @CordaInject lateinit var service: SingletonSerializeAsToken
        override fun call(session: FlowSession) = Unit
    }
    class CustomServiceFlow : ResponderFlow {
        @CordaInject lateinit var service: MyService
        override fun call(session: FlowSession) = Unit
    }
    class MyService
}
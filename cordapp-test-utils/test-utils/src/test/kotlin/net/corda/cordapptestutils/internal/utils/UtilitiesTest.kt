package net.corda.cordapptestutils.internal.utils

import net.corda.cordapptestutils.internal.testflows.HelloFlow
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.base.types.MemberX500Name
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class UtilitiesTest {

    @Test
    fun `should provide injector into flows`() {
        val flowEngine = mock<FlowEngine>()
        val flow = HelloFlow()
        flow.injectIfRequired(FlowEngine::class.java, flowEngine)
        assertThat(flow.flowEngine, `is`(flowEngine))
    }

    @Test
    fun `should provide unique sandbox names based on member names`() {
        val member = MemberX500Name.parse("CN=IRunCordapps, OU=Application, O=R3, L=London, C=GB")
        assertThat(member.sandboxName,
            `is`("CN_IRunCordapps_OU_Application_O_R3_L_London_C_GB"))
    }
}
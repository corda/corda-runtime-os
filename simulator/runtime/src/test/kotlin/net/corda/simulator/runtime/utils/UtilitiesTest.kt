package net.corda.simulator.runtime.utils

import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.factories.ServiceOverrideBuilder
import net.corda.simulator.runtime.testflows.HelloFlow
import net.corda.v5.application.crypto.MerkleTreeFactory
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UtilitiesTest {

    companion object {

        open class A : Flow {
            @CordaInject
            lateinit var inheritedField: String
        }

        class B : A() {
            @CordaInject
            lateinit var declaredField: Any
        }

        class NonImplementedServiceFlow : ResponderFlow {
            @CordaInject lateinit var service: SingletonSerializeAsToken
            override fun call(session: FlowSession) = Unit
        }
        class CustomServiceFlow : ResponderFlow {
            @CordaInject lateinit var service: MyService
            override fun call(session: FlowSession) = Unit
        }

        class OverrideServiceFlow : Flow {
            @CordaInject lateinit var service: MerkleTreeFactory
        }
        class MyService

    }

    @Test
    fun `should be able to inject into inherited and non-inherited fields`() {
        // Given two fields, one of which is declared and one which is not
        val b = B()

        // When we inject into both of them
        b.injectIfRequired(String::class.java) { "Hello!" }
        b.injectIfRequired(Any::class.java) { Object() }

        // Then both fields should be set
        Assertions.assertNotNull(b.declaredField)
        Assertions.assertNotNull(b.inheritedField)
    }

    @Test
    fun `should provide unique sandbox names based on member names`() {
        val member = MemberX500Name.parse("CN=IRunCordapps, OU=Application, O=R3, L=London, C=GB")
        assertThat(member.sandboxName,
            `is`("CN_IRunCordapps_OU_Application_O_R3_L_London_C_GB"))
    }

    @Test
    fun `should throw error when non supported service is injected to a flow`() {
        val config = mock<SimulatorConfiguration>()
        val overrideBuilder = ServiceOverrideBuilder<MerkleTreeFactory> { _, _, _ -> mock() }
        whenever(config.serviceOverrides).thenReturn(mapOf(Pair(MerkleTreeFactory::class.java, overrideBuilder)))

        val flow1 = NonImplementedServiceFlow()
        assertThrows<NotImplementedError> {
            checkAPIAvailability(flow1, config)
        }

        val flow2 = CustomServiceFlow()
        assertThrows<NotImplementedError> {
            checkAPIAvailability(flow2, config)
        }

        val flow3 = OverrideServiceFlow()
        assertDoesNotThrow {
            checkAPIAvailability(flow3, config)
        }

        val flow4 = HelloFlow()
        assertDoesNotThrow {
            checkAPIAvailability(flow4, config)
        }
    }
}
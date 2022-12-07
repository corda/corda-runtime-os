package net.corda.simulator.runtime.tools

import net.corda.simulator.exceptions.NoProtocolAnnotationException
import net.corda.simulator.runtime.testflows.PingAckFlow
import net.corda.simulator.runtime.testflows.PingAckResponderFlow
import net.corda.simulator.runtime.utils.getProtocol
import net.corda.simulator.runtime.utils.injectIfRequired
import net.corda.simulator.runtime.utils.sandboxName
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.Flow
import net.corda.v5.base.types.MemberX500Name
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows


class UtilitiesTest {

    @Suppress("ForbiddenComment")
    /**
     * TODO: Watch https://r3-cev.atlassian.net/browse/CORE-5987 -
     * if fixed in Corda, this should also do inherited fields.
     */
    @Test
    fun `should extend Flow with inject but only for declared fields`() {
        // Given two fields, one of which is declared and one which is not
        val b = B()

        // When we inject into both of them
        b.injectIfRequired(String::class.java) { "Hello!" }
        b.injectIfRequired(Any::class.java) { Object() }

        // Then the declared field should be set, but not the inherited one
        assertNotNull(b.declaredField)
        assertThrows<UninitializedPropertyAccessException>{ b.inheritedField }
    }

    @Test
    fun `should extend MemberX500 names with something that returns a unique db-friendly string back`() {
        // Given a member
        val member = MemberX500Name.parse("CN=IRunCorDapps, OU=Application, O=R3, L=London, C=GB")

        // When we turn it into a db-friendly name
        val name = member.sandboxName

        // Then it should be something readable and unique
        assertThat(name, `is`("CN_IRunCorDapps_OU_Application_O_R3_L_London_C_GB"))
    }

    open class A : Flow {
        @CordaInject
        lateinit var inheritedField: String
    }

    @Test
    fun `should return protocol from the flow class`(){
        val initiatorFlow = PingAckFlow()
        val responderFlow = PingAckResponderFlow()
        val noProtocolFlow = A()

        assertEquals("ping-ack", initiatorFlow.getProtocol())
        assertEquals("ping-ack", responderFlow.getProtocol())
        assertThrows<NoProtocolAnnotationException>{noProtocolFlow.getProtocol()}
    }

    class B : A() {
        @CordaInject
        lateinit var declaredField: Any
    }
}
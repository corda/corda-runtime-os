package net.corda.simulator.runtime.flows

import net.corda.simulator.runtime.messaging.SimFiberBase
import net.corda.simulator.runtime.testflows.HelloFlow
import net.corda.v5.base.types.MemberX500Name
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class DefaultServicesInjectorTest {

    @Test
    fun `should inject sensible defaults for services`() {
        // Given a flow
        val flow = HelloFlow()

        // With some helpful classes to use in services
        val member = MemberX500Name.parse("CN=IRunCorDapps, OU=Application, O=R3, L=London, C=GB")
        val fiber = SimFiberBase()

        fiber.use {
            // When we inject services into it
            DefaultServicesInjector(mock()).injectServices(flow, member, it)

            // Then it should have constructed useful things for us
            assertNotNull(flow.flowEngine)
            assertNotNull(flow.jsonMarshallingService)
            assertNotNull(flow.persistenceService)
            assertNotNull(flow.flowMessaging)
            assertNotNull(flow.memberLookup)
            assertNotNull(flow.signingService)
            assertNotNull(flow.signatureVerificationService)
        }
    }
}
package net.corda.simulator.runtime.flows

import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.factories.ServiceOverrideBuilder
import net.corda.simulator.runtime.messaging.SimFiberBase
import net.corda.simulator.runtime.testflows.HelloFlow
import net.corda.v5.application.crypto.MerkleTreeFactory
import net.corda.v5.application.crypto.SignatureSpecService
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.types.MemberX500Name
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock

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
            assertNotNull(flow.signatureSpecService)
        }
    }

    @Test
    fun `should throw an error if a service is in a flow that is not recognized as a Corda service`() {
        // Given a flow
        val flow = FlowWithNonCordaService()
        val member = MemberX500Name.parse("CN=IRunCorDapps, OU=Application, O=R3, L=London, C=GB")
        val fiber = SimFiberBase()

        fiber.use {
            // When we inject services into it
            // Then it should throw an exception
            assertThrows<NotImplementedError> {
                DefaultServicesInjector(mock()).injectServices(flow, member, it)
            }
        }
    }

    @Test
    fun `should inject existing services into overrides or use null if no service available`() {
        // Given an override for a service which we do support
        val myJsonMarshallingService = mock<JsonMarshallingService>()
        var capturedJsonMarshallingService: JsonMarshallingService? = null
        val myJMSBuilder = ServiceOverrideBuilder<JsonMarshallingService>{_, _, service ->
            capturedJsonMarshallingService = service
            myJsonMarshallingService
        }

        // And an override for one we don't support yet
        // (Of course the MerkleTreeFactory isn't a service but this is standing in for something we might not support)
        val myFactory = mock<MerkleTreeFactory>()
        var capturedFactory: MerkleTreeFactory? = null
        val myFactoryBuilder = ServiceOverrideBuilder<MerkleTreeFactory>{ _, _, service ->
            capturedFactory = service
            myFactory
        }

        // When we inject the services with a config with those overrides
        val config = mock<SimulatorConfiguration>()
        whenever(config.serviceOverrides).thenReturn(mapOf(
            JsonMarshallingService::class.java to myJMSBuilder,
            MerkleTreeFactory::class.java to myFactoryBuilder
        ))

        val flow = FlowForOverrides()
        val member = MemberX500Name.parse("CN=IRunCorDapps, OU=Application, O=R3, L=London, C=GB")
        val fiber = SimFiberBase()

        fiber.use {
            // When we inject services into it
            DefaultServicesInjector(config).injectServices(flow, member, it)
        }

        // Then the flow should have the overridden services
        assertThat(flow.jsonMarshallingService, `is`(myJsonMarshallingService))
        assertThat(flow.merkleTreeFactory, `is`(myFactory))

        // And the services we do support should have been passed in
        assertNotNull(capturedJsonMarshallingService)
        assertNull(capturedFactory)

        // And we should still have got the one service that we didn't override
        assertNotNull(flow.specService)
    }

    class FlowForOverrides : Flow {
        @CordaInject
        lateinit var jsonMarshallingService: JsonMarshallingService

        @CordaInject
        lateinit var merkleTreeFactory: MerkleTreeFactory

        @CordaInject
        lateinit var specService: SignatureSpecService
    }

    class FlowWithNonCordaService : Flow {
        @CordaInject
        lateinit var clock: Clock
    }
}
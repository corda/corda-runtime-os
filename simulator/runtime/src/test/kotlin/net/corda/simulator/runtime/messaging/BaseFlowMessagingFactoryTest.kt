package net.corda.simulator.runtime.messaging

import net.corda.simulator.runtime.config.DefaultConfigurationBuilder
import net.corda.simulator.runtime.flows.FlowServicesInjector
import net.corda.simulator.runtime.testflows.PingAckFlow
import net.corda.simulator.runtime.testflows.PingAckResponderFlow
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class BaseFlowMessagingFactoryTest {

    private val memberA = MemberX500Name.parse("CN=CorDapperA, OU=Application, O=R3, L=London, C=GB")

    @Test
    fun `should create flow messaging for instance flow`(){
        // Given an initiator and responder instance flow
        val initiator = mock<RPCStartableFlow>()
        val responder = mock<ResponderFlow>()
        val injector = mock<FlowServicesInjector>()
        val fiber = SimFiberBase()

        fiber.registerFlowInstance(memberA, "protocol", initiator)
        fiber.registerFlowInstance(memberA, "protocol", responder)

        // When we call the factory create method for the flow
        val flowMessagingA =  BaseFlowMessagingFactory().createFlowMessaging(
            DefaultConfigurationBuilder().build(), memberA, fiber, injector, initiator)

        val flowMessagingB =  BaseFlowMessagingFactory().createFlowMessaging(
            DefaultConfigurationBuilder().build(), memberA, fiber, injector, responder)

        // Then the factory should return a flow messaging object
        assertThat(flowMessagingA is ConcurrentFlowMessaging)
        assertThat(flowMessagingB is ConcurrentFlowMessaging);

    }

    @Test
    fun `should create flow messaging for class flow`(){
        // Given an initiator and responder class flow
        val initiator = PingAckFlow()
        val responder = PingAckResponderFlow()
        val injector = mock<FlowServicesInjector>()
        val fiber = SimFiberBase()

        // When we call the factory create method for the flow
        val flowMessagingA =  BaseFlowMessagingFactory().createFlowMessaging(
            DefaultConfigurationBuilder().build(), memberA, fiber, injector, initiator)
        val flowMessagingB =  BaseFlowMessagingFactory().createFlowMessaging(
            DefaultConfigurationBuilder().build(), memberA, fiber, injector, responder)

        // Then the factory should return a flow messaging object
        assertThat(flowMessagingA is ConcurrentFlowMessaging)
        assertThat(flowMessagingB is ConcurrentFlowMessaging);

    }
}
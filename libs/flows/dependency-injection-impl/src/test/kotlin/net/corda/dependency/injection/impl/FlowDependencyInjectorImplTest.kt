package net.corda.dependency.injection.impl

import net.corda.dependency.injection.InjectableFactory
import net.corda.flow.statemachine.FlowStateMachine
import net.corda.sandbox.SandboxGroup
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.injection.CordaInject
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class FlowDependencyInjectorImplTest {
    private val s1: SingletonSerializeAsToken = mock()
    private val s2: SingletonSerializeAsToken = mock()

    private val fac1 = Service1Factory(Service1::class.java)
    private val fac2 = Service2Factory(Service2::class.java)

    private val sandboxGroup: SandboxGroup = mock()
    private val flowStateMachine: FlowStateMachine<ExampleFlow> = mock()

    private var flowDependencyInjector = FlowDependencyInjectorImpl(sandboxGroup, listOf(fac1, fac2), listOf(s1, s2))

    @Test
    fun `get singletons returns all singletons`() {
        val results = flowDependencyInjector.getRegisteredAsTokenSingletons()

        assertThat(results).containsExactly(s1, s2)
    }

    @Test
    fun `inject services uses factories to inject services into flows`() {
        val flow = ExampleFlow()
        flowDependencyInjector.injectServices(flow, flowStateMachine)

        val service1 = flow.service1 as Service1Impl
        val service2 = flow.service2 as Service2Impl

        assertThat(service1).isNotNull
        assertThat(service2).isNotNull

        // Assert the factories were passed the state machine and sandbox
        assertThat(service1.flowStateMachine).isSameAs(flowStateMachine)
        assertThat(service2.flowStateMachine).isSameAs(flowStateMachine)

        assertThat(service1.sandboxGroup).isSameAs(sandboxGroup)
        assertThat(service2.sandboxGroup).isSameAs(sandboxGroup)
    }

    @Test
    fun `an exception is thrown if the flow request a type that is unregistered`() {
        val flow = ExampleInvalidFlow()
        Assertions.assertThatIllegalArgumentException()
            .isThrownBy { flowDependencyInjector.injectServices(flow, flowStateMachine) }
            .withMessage("No registered types could be found for the following field(s) 'service'")
    }

    @Test
    fun `an exception is thrown if the same service is registered more than once`() {
        Assertions.assertThatIllegalArgumentException()
            .isThrownBy { FlowDependencyInjectorImpl(sandboxGroup, listOf(fac1, fac1), listOf(s1, s2)) }
            .withMessage("An instance of type 'net.corda.dependency.injection.impl.Service1' has been already been registered.")
    }
}

/**
 * Example Service 1
 */
interface Service1
class Service1Impl(val flowStateMachine: FlowStateMachine<*>, val sandboxGroup: SandboxGroup) : Service1
class Service1Factory(
    override val target: Class<Service1>
) : InjectableFactory<Service1> {

    override fun create(flowStateMachine: FlowStateMachine<*>, sandboxGroup: SandboxGroup): Service1 {
        return Service1Impl(flowStateMachine, sandboxGroup)
    }
}

/**
 * Example Service 2
 */
interface Service2
class Service2Impl(val flowStateMachine: FlowStateMachine<*>, val sandboxGroup: SandboxGroup) : Service2
class Service2Factory(
    override val target: Class<Service2>,
) : InjectableFactory<Service2> {

    override fun create(flowStateMachine: FlowStateMachine<*>, sandboxGroup: SandboxGroup): Service2 {
        return Service2Impl(flowStateMachine, sandboxGroup)
    }
}

/**
 * Example Flows
 */
class ExampleFlow : Flow<String> {
    @CordaInject
    lateinit var service1: Service1

    @CordaInject
    lateinit var service2: Service2

    override fun call(): String {
        return ""
    }
}

interface UnavailableService
class ExampleInvalidFlow : Flow<String> {
    @CordaInject
    lateinit var service: UnavailableService

    override fun call(): String {
        return ""
    }
}


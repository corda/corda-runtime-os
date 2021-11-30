package net.corda.dependency.injection.impl

import net.corda.dependency.injection.InjectableFactory
import net.corda.flow.statemachine.FlowStateMachine
import net.corda.sandbox.SandboxGroup
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.injection.CordaInject
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class FlowDependencyInjectorImplTest {
    private val s1: SingletonSerializeAsToken = mock()
    private val s2: SingletonSerializeAsToken = mock()
    private val s3: SingletonSerializeAsToken = mock()

    private val fac1 = Service1Factory(Service1::class.java, setOf(s1, s2))
    private val fac2 = Service2Factory(Service2::class.java, setOf(s3))

    private val sandboxGroup: SandboxGroup = mock()
    private val flowStateMachine: FlowStateMachine<ExampleFlow> = mock()

    private var flowDependencyInjector= FlowDependencyInjectorImpl(sandboxGroup, listOf(fac1, fac2))

    @Test
    fun `get singletons returns all singletons from all factories`() {
        var results = flowDependencyInjector.getRegisteredAsTokenSingletons()

        assertThat(results).containsExactly(s1, s2, s3)
    }

    @Test
    fun `inject services uses factories to inject services into flows`() {
        var flow = ExampleFlow()
        flowDependencyInjector.injectServices(flow, flowStateMachine)

        var service1 = flow.service1 as Service1Impl
        var service2 = flow.service2 as Service2Impl

        assertThat(service1).isNotNull
        assertThat(service2).isNotNull

        // Assert the factories were passed the state machine and sandbox
        assertThat(service1.flowStateMachine).isSameAs(flowStateMachine)
        assertThat(service2.flowStateMachine).isSameAs(flowStateMachine)

        assertThat(service1.sandboxGroup).isSameAs(sandboxGroup)
        assertThat(service2.sandboxGroup).isSameAs(sandboxGroup)
    }
}

interface Service1
class Service1Impl(val flowStateMachine: FlowStateMachine<*>, val sandboxGroup: SandboxGroup) : Service1
class Service1Factory(
    override val target: Class<Service1>,
    private val singletonSet: Set<SingletonSerializeAsToken>
) : InjectableFactory<Service1> {

    override fun getSingletons(): Set<SingletonSerializeAsToken> {
        return singletonSet
    }

    override fun create(flowStateMachine: FlowStateMachine<*>, sandboxGroup: SandboxGroup): Service1 {
        return Service1Impl(flowStateMachine, sandboxGroup)
    }
}

interface Service2
class Service2Impl(val flowStateMachine: FlowStateMachine<*>, val sandboxGroup: SandboxGroup) : Service2
class Service2Factory(
    override val target: Class<Service2>,
    private val singletonSet: Set<SingletonSerializeAsToken>
) : InjectableFactory<Service2> {

    override fun getSingletons(): Set<SingletonSerializeAsToken> {
        return singletonSet
    }

    override fun create(flowStateMachine: FlowStateMachine<*>, sandboxGroup: SandboxGroup): Service2 {
        return Service2Impl(flowStateMachine, sandboxGroup)
    }
}

class ExampleFlow : Flow<String> {
    @CordaInject
    lateinit var service1: Service1

    @CordaInject
    lateinit var service2: Service2

    override fun call(): String {
        return ""
    }
}


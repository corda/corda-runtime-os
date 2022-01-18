package net.corda.flow.manager.impl

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.application.injection.CordaInject
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class SandboxDependencyInjectorImplTest {
    private val s1 = Service1Impl()
    private val s2 = Service2Impl()

    private var flowDependencyInjector = SandboxDependencyInjectorImpl(listOf(s1, s2))

    @Test
    fun `get singletons returns all singletons`() {
        val results = flowDependencyInjector.getRegisteredSingletons()
        Assertions.assertThat(results).containsExactly(s1, s2)
    }

    @Test
    fun `inject services uses factories to inject services into flows`() {
        val flow = ExampleFlow()
        flowDependencyInjector.injectServices(flow)

        val service1 = flow.service1 as Service1Impl
        val service2 = flow.service2 as Service2Impl

        Assertions.assertThat(service1).isNotNull
        Assertions.assertThat(service2).isNotNull
    }

    @Test
    fun `an exception is thrown if the flow request a type that is unregistered`() {
        val flow = ExampleInvalidFlow()
        Assertions.assertThatIllegalArgumentException()
            .isThrownBy { flowDependencyInjector.injectServices(flow) }
            .withMessage("No registered types could be found for the following field(s) 'service'")
    }

    @Test
    fun `an exception is thrown if the same interface is implemented by more than once service`() {
        Assertions.assertThatIllegalArgumentException()
            .isThrownBy { SandboxDependencyInjectorImpl(listOf(s2, DuplicateService2Impl())) }
            .withMessage("An implementation of type 'net.corda.flow.manager.impl.Service2' has been already been " +
                    "registered by 'net.corda.flow.manager.impl.Service2Impl' it can't be registered again " +
                    "by 'net.corda.flow.manager.impl.DuplicateService2Impl'.")
    }

    interface Service1
    class Service1Impl : Service1, SingletonSerializeAsToken

    interface Service2
    class Service2Impl : Service2, SingletonSerializeAsToken, CordaFlowInjectable

    class DuplicateService2Impl : Service2, SingletonSerializeAsToken, CordaFlowInjectable

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
}
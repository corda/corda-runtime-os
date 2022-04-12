package net.corda.flow.pipeline.sandbox

import net.corda.flow.pipeline.sandbox.impl.SandboxDependencyInjectorImpl
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.application.injection.CordaInject
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class SandboxDependencyInjectorImplTest {
    private val s1 = Service1Impl()
    private val s2 = Service2Impl()

    private val serviceTypes1 = arrayOf(
        Service1::class.java.name,
        SingletonSerializeAsToken::class.java.name
    )
    private val serviceTypes2 = arrayOf(
        Service2::class.java.name,
        SingletonSerializeAsToken::class.java.name,
        CordaFlowInjectable::class.java.name
    )
    private val flowDependencyInjector = SandboxDependencyInjectorImpl(mapOf(s1 to serviceTypes1, s2 to serviceTypes2), mock())

    @Test
    fun `get singletons returns all singletons`() {
        val results = flowDependencyInjector.getRegisteredSingletons()
        Assertions.assertThat(results).containsExactly(s1, s2)
    }

    @Test
    fun `inject services uses factories to inject services into flows`() {
        val flow = ExampleFlow()
        flowDependencyInjector.injectServices(flow)

        val service1 = flow.service1
        val service2 = flow.service2

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
            .isThrownBy {
                SandboxDependencyInjectorImpl(
                    mapOf(
                        s2 to serviceTypes2,
                        DuplicateService2Impl() to serviceTypes2
                    )
                ) {}
            }
            .withMessage(
                "An implementation of type '${Service2::class.qualifiedName}' has been already been " +
                        "registered by '${Service2Impl::class.qualifiedName}' it can't be registered again " +
                        "by '${DuplicateService2Impl::class.qualifiedName}'."
            )
    }
}

interface Service1
class Service1Impl : Service1, SingletonSerializeAsToken

interface Service2
class Service2Impl : Service2, SingletonSerializeAsToken, CordaFlowInjectable

class DuplicateService2Impl : Service2, SingletonSerializeAsToken, CordaFlowInjectable

class ExampleFlow : Flow<String> {
    @CordaInject
    internal lateinit var service1: Service1

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

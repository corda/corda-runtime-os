package net.corda.flow.pipeline.sandbox

import net.corda.flow.pipeline.sandbox.impl.SandboxDependencyInjectorImpl
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.RestStartableFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class SandboxDependencyInjectorImplTest {
    private val s1 = Service1Impl()
    private val s2 = Service2Impl()
    private val s3 = SharedServiceImpl()

    private val serviceTypes1 = listOf(
        Service1::class.java.name
    )
    private val serviceTypes2 = listOf(
        Service2::class.java.name
    )
    private val serviceTypes3 = listOf(
        SharedService::class.java.name
    )
    private val flowDependencyInjector =
        SandboxDependencyInjectorImpl(mapOf(s1 to serviceTypes1, s2 to serviceTypes2, s3 to serviceTypes3), mock())

    @Test
    fun `get services returns all services`() {
        val results = flowDependencyInjector.getRegisteredServices()
        assertThat(results).containsExactly(s1, s2, s3)
    }

    @Test
    fun `inject services uses factories to inject services into flows`() {
        val flow = ExampleFlow()
        flowDependencyInjector.injectServices(flow)

        val service1 = flow.service1
        val service2 = flow.service2

        assertThat(service1).isNotNull
        assertThat(service2).isNotNull
    }

    @Test
    fun `an exception is thrown if the flow request a type that is unregistered`() {
        val flow = ExampleInvalidFlow()
        assertThatIllegalArgumentException()
            .isThrownBy { flowDependencyInjector.injectServices(flow) }
            .withMessage("No registered types could be found for the following field(s) 'service'")
    }

    @Test
    fun `an exception is thrown if the same interface is implemented by more than once service`() {
        assertThatIllegalArgumentException()
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

    @Test
    fun `allows the injection of fields and usage of common logic inherited from parent flows`() {
        val flow = ChildFlow()
        flowDependencyInjector.injectServices(flow)

        assertThat(flow.service1).isNotNull
        assertThat(flow.service2).isNotNull
        assertThat(flow.sharedService).isNotNull
        assertThat(flow.call()).isEqualTo(SharedService.from(ChildFlow::class.java.simpleName))
    }

    @Test
    fun `allows the injection of fields and usage of common logic inherited from abstract flows and interfaces`() {
        val flow = ConcreteChildFlow()
        flowDependencyInjector.injectServices(flow)

        assertThat(flow.service1).isNotNull
        assertThat(flow.service2).isNotNull
        assertThat(flow.sharedService).isNotNull
        assertThat(flow.call(mock())).isEqualTo(SharedService.from(ConcreteChildFlow::class.java.simpleName))
    }

    @Test
    fun `a flow which injects two of the same instance runs okay`() {
        val flow = DoubleInjectedFlow()
        flowDependencyInjector.injectServices(flow)

        assertThat(flow.service1).isSameAs(flow.service1Again)
    }
}

interface Service1
class Service1Impl : Service1, UsedByFlow, SingletonSerializeAsToken

interface Service2
class Service2Impl : Service2, UsedByFlow, SingletonSerializeAsToken

class DuplicateService2Impl : Service2, UsedByFlow, SingletonSerializeAsToken

class ExampleFlow : SubFlow<String> {
    @CordaInject
    internal lateinit var service1: Service1

    @CordaInject
    lateinit var service2: Service2

    override fun call(): String {
        return ""
    }
}

interface UnavailableService
class ExampleInvalidFlow : SubFlow<String> {
    @CordaInject
    lateinit var service: UnavailableService

    override fun call(): String {
        return ""
    }
}

// Dummy service shared between flows
interface SharedService {
    companion object {
        const val START_TOKEN = "["
        const val FINISH_TOKEN = "]"
        fun from(str: String): String = StringBuilder().append(START_TOKEN).append(str).append(FINISH_TOKEN).toString()
    }

    fun start()

    fun process(str: String)

    fun finish()

    fun get(): String
}

class SharedServiceImpl : SharedService, UsedByFlow, SingletonSerializeAsToken {
    private val builder = StringBuilder()

    override fun start() {
        builder.append(SharedService.START_TOKEN)
    }

    override fun process(str: String) {
        builder.append(str)
    }

    override fun finish() {
        builder.append(SharedService.FINISH_TOKEN)
    }

    override fun get(): String {
        return builder.toString()
    }
}

// Test scenarios on which users have common logic inherited between concrete flows.
open class ConcreteParentFlow : SubFlow<String> {
    @CordaInject
    lateinit var service1: Service1

    @CordaInject
    internal lateinit var service2: Service2

    @CordaInject
    lateinit var sharedService: SharedService

    private fun beginProcessing() {
        sharedService.start()
    }

    open fun process() {
    }

    private fun doneProcessing() {
        sharedService.finish()
    }

    open fun templateMethod(): String {
        beginProcessing()
        process()
        doneProcessing()

        return sharedService.get()
    }

    override fun call(): String {
        return templateMethod()
    }
}

class ChildFlow : ConcreteParentFlow() {
    override fun process() {
        sharedService.process(this::class.java.simpleName)
    }
}

// Test scenarios on which users have common logic inherited from abstract flows.

interface CustomFlowInterface {
    fun method(): String
}

abstract class AbstractParentFlow : RestStartableFlow, CustomFlowInterface {
    @CordaInject
    lateinit var service1: Service1

    @CordaInject
    internal lateinit var service2: Service2

    @CordaInject
    lateinit var sharedService: SharedService
}

class ConcreteChildFlow : AbstractParentFlow() {
    override fun method(): String {
        sharedService.start()
        sharedService.process(this::class.java.simpleName)
        sharedService.finish()

        return sharedService.get()
    }

    override fun call(requestBody: RestRequestBody): String {
        return method()
    }
}

class DoubleInjectedFlow : RestStartableFlow {

    @CordaInject
    lateinit var service1: Service1

    @CordaInject
    lateinit var service1Again: Service1

    override fun call(requestBody: RestRequestBody): String {
        return ""
    }

}

package net.corda.dependency.injection.impl

import net.corda.dependency.injection.CordaInjectableException
import net.corda.flow.statemachine.FlowStateMachine
import net.corda.v5.application.flows.Flow
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Tests [DependencyInjectionServiceImpl]. */
class DependencyInjectionServiceTests {
    private companion object {
        private val dependencyInjectionService = DependencyInjectionServiceImpl()

        init {
            dependencyInjectionService.registerSingletonService(CordaFlowInjectableImpl::class.java, mock())
            dependencyInjectionService.registerSingletonService(CordaServiceInjectableImpl::class.java, mock())
            dependencyInjectionService.registerSingletonService(CordaServiceAndFlowInjectableImpl::class.java, mock())
        }
    }

    /** Creates a mock [FlowStateMachine] that returns the provided [flow]. */
    private fun createMockStateMachine(flow: Flow<*>) = mock<FlowStateMachine<*>>().apply {
        whenever(getFlowLogic()).thenReturn(flow)
    }

    @Test
    fun `Can inject services into a flow`() {
        val flow = InjectDependenciesFlow()
        val stateMachine = createMockStateMachine(flow)
        dependencyInjectionService.injectDependencies(flow, stateMachine)
        assertTrue(flow.isInitialized())
        assertTrue(flow.isCallable())
    }

    @Test
    fun `Can inject services into a flow's superclasses`() {
        val flow = InheritingFlow()
        val stateMachine = createMockStateMachine(flow)
        dependencyInjectionService.injectDependencies(flow, stateMachine)
        assertTrue(flow.isInitialized())
        assertTrue(flow.isCallable())
    }

    @Test
    fun `Can inject services into a Corda service`() {
        val service = InjectDependenciesService()
        dependencyInjectionService.injectDependencies(service)
        assertTrue(service.isInitialized())
        assertTrue(service.isCallable())
    }

    @Test
    fun `Can inject services into a Corda service's superclasses`() {
        val service = InheritingCordaService()
        dependencyInjectionService.injectDependencies(service)
        assertTrue(service.isInitialized())
        assertTrue(service.isCallable())
    }

    @Test
    fun `Cannot inject interface without annotation into a flow`() {
        val flow = InvalidDependencySetupFlow()
        val stateMachine = createMockStateMachine(flow)
        dependencyInjectionService.injectDependencies(flow, stateMachine)
        assertFalse(flow.isInitialized())
    }

    @Test
    fun `Cannot inject interface without annotation into a Corda service`() {
        val service = InvalidDependencySetupService()
        dependencyInjectionService.injectDependencies(service)
        assertFalse(service.isInitialized())
    }

    @Test
    fun `Cannot inject a non-injectable interface into a flow`() {
        val flow = InvalidDependencyFlow()
        val stateMachine = createMockStateMachine(flow)
        assertThrows<CordaRuntimeException> {
            dependencyInjectionService.injectDependencies(flow, stateMachine)
        }
        assertFalse(flow.isInitialised())
    }

    @Test
    fun `Cannot inject a non-injectable interface into a Corda service`() {
        val service = InvalidDependencyService()
        assertThrows<CordaRuntimeException> {
            dependencyInjectionService.injectDependencies(service)
        }
        assertFalse(service.isInitialised())
    }

    @Test
    fun `Cannot inject a service injectable into a flow`() {
        val flow = FlowUsingCordaServiceInjectable()
        val stateMachine = createMockStateMachine(flow)
        assertThrows<CordaInjectableException> {
            dependencyInjectionService.injectDependencies(flow, stateMachine)
        }
        assertFalse(flow.isInitialised())
    }

    @Test
    fun `Cannot inject a flow injectable into a Corda service`() {
        val service = CordaServiceUsingFlowInjectable()
        assertThrows<CordaInjectableException> {
            dependencyInjectionService.injectDependencies(service)
        }
        assertFalse(service.isInitialized())
    }
}
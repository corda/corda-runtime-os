package net.corda.dependency.injection.impl

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.application.injection.CordaInject
import net.corda.v5.application.injection.CordaServiceInjectable
import net.corda.v5.application.services.CordaService

/** A flow that requires the injection of services. */
open class InjectDependenciesFlow : Flow<Unit> {
    @CordaInject
    private lateinit var cordaFlowInjectable: CordaFlowInjectableImpl

    @CordaInject
    private lateinit var cordaServiceAndFlowInjectable: CordaServiceAndFlowInjectableImpl

    override fun call() = Unit

    fun isInitialized() = ::cordaFlowInjectable.isInitialized && ::cordaServiceAndFlowInjectable.isInitialized

    fun isCallable() = try {
        cordaFlowInjectable.call()
        cordaServiceAndFlowInjectable.call()
        true
    } catch (e: UninitializedPropertyAccessException) {
        false
    }
}

/** A Corda service that requires the injection of services. */
open class InjectDependenciesService : CordaService {
    @CordaInject
    private lateinit var cordaServiceInjectable: CordaServiceInjectableImpl

    @CordaInject
    private lateinit var cordaServiceAndFlowInjectable: CordaServiceAndFlowInjectableImpl

    fun isInitialized() = ::cordaServiceInjectable.isInitialized && ::cordaServiceAndFlowInjectable.isInitialized

    fun isCallable() = try {
        cordaServiceInjectable.call()
        cordaServiceAndFlowInjectable.call()
        true
    } catch (e: UninitializedPropertyAccessException) {
        false
    }
}

/** A flow whose superclass requires the injection of services. */
class InheritingFlow : InjectDependenciesFlow()

/** A Corda service whose superclass requires the injection of services. */
class InheritingCordaService : InjectDependenciesService()

/** A flow that is not annotated correctly for injection. */
class InvalidDependencySetupFlow : Flow<Unit> {
    // `@CordaInject` annotation is missing.
    private lateinit var cordaFlowInjectable: CordaFlowInjectableImpl

    override fun call() = Unit

    fun isInitialized() = ::cordaFlowInjectable.isInitialized
}

/** A Corda service that is not annotated correctly for injection. */
class InvalidDependencySetupService : CordaService {
    // `@CordaInject` annotation is missing.
    private lateinit var cordaServiceAndFlowInjectable: CordaServiceAndFlowInjectableImpl

    fun isInitialized() = ::cordaServiceAndFlowInjectable.isInitialized
}

/** A flow that tries to inject an uninjectable class. */
class InvalidDependencyFlow : Flow<Unit> {
    @Suppress("unused")
    @CordaInject
    private lateinit var uninjectableDependency: String

    override fun call() = Unit

    fun isInitialised() = ::uninjectableDependency.isInitialized
}

/** A Corda service that tries to inject an uninjectable class. */
class InvalidDependencyService: CordaService {
    @Suppress("unused")
    @CordaInject
    private lateinit var uninjectableDependency: String

    fun isInitialised() = ::uninjectableDependency.isInitialized
}

/** A flow that tries to inject a Corda service injectable. */
class FlowUsingCordaServiceInjectable : Flow<Unit> {
    @CordaInject
    private lateinit var cordaServiceInjectable: CordaServiceInjectableImpl

    override fun call() = Unit

    fun isInitialised() = ::cordaServiceInjectable.isInitialized
}

/** A Corda service that tries to inject a flow injectable. */
class CordaServiceUsingFlowInjectable : CordaService {
    @CordaInject
    private lateinit var cordaFlowInjectable: CordaFlowInjectableImpl

    fun isInitialized() = ::cordaFlowInjectable.isInitialized
}

/** A dummy flow injectable. */
class CordaFlowInjectableImpl: CordaFlowInjectable {
    fun call() = Unit
}

/** A dummy service injectable. */
class CordaServiceInjectableImpl: CordaServiceInjectable {
    fun call() = Unit
}

/** A dummy service and flow injectable. */
class CordaServiceAndFlowInjectableImpl: CordaServiceInjectable, CordaFlowInjectable {
    fun call() = Unit
}
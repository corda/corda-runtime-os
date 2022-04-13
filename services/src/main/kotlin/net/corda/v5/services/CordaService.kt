package net.corda.v5.services

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.application.injection.CordaServiceInjectable
import net.corda.v5.serialization.SingletonSerializeAsToken

/**
 * Any class that needs to be a long-lived service within the node, should implement this interface. The constructor will be invoked during
 * node start to initialise the service, however initialisation logic is better added to the [ServiceLifecycleObserver.onEvent] function as
 * a response to the [ServiceStart] implementation of [ServiceLifecycleEvent] so that injected services can be used. Injected services are
 * injected post instantiation so no injected services can be used within a constructor. Corda services are created as singletons within the
 * node and can be made available to flows and other corda services via dependency injection.
 *
 * All corda services can become available for injection to flows or corda services by additionally implementing [CordaFlowInjectable] or
 * [CordaServiceInjectable] respectively or both. They can be injected into flows, other corda services, or notary services by using the
 * [CordaInject] or [CordaInjectPreStart] annotations. Dependencies annotated with [CordaInjectPreStart] will be made available for use
 * before the [ServiceStart] lifecycle event is distributed, whereas [CordaInject] dependencies will be injected after the service has
 * started.
 */
interface CordaService : ServiceLifecycleObserver, SingletonSerializeAsToken

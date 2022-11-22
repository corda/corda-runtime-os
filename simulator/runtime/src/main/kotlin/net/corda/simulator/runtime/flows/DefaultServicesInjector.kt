package net.corda.simulator.runtime.flows

import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.exceptions.NoProtocolAnnotationException
import net.corda.simulator.factories.ServiceOverrideBuilder
import net.corda.simulator.runtime.messaging.ConcurrentFlowMessaging
import net.corda.simulator.runtime.messaging.FlowContext
import net.corda.simulator.runtime.messaging.SimFiber
import net.corda.simulator.runtime.signing.OnlyOneSignatureSpecService
import net.corda.simulator.runtime.signing.SimKeyStore
import net.corda.simulator.runtime.signing.SimWithJsonSignatureVerificationService
import net.corda.simulator.runtime.signing.SimWithJsonSigningService
import net.corda.simulator.runtime.tools.SimpleJsonMarshallingService
import net.corda.simulator.runtime.utils.checkAPIAvailability
import net.corda.simulator.runtime.utils.availableAPIs
import net.corda.simulator.runtime.utils.injectIfRequired
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SignatureSpecService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger

/**
 * Injector for default services for Simulator.
 */
@Suppress("TooManyFunctions")
class DefaultServicesInjector(private val configuration: SimulatorConfiguration) : FlowServicesInjector {
    private companion object {
        val log = contextLogger()
    }

    /**
     * Injects sensible default services into the provided flow. Currently injects:<br>
     * <ul>
     *   <li>JsonMarshallingService
     *   <li>FlowEngine
     *   <li>FlowMessaging
     * </ul>
     * As with the real Corda, injected service properties must be marked with the @CordaInject annotation.
     *
     * @param flow The flow to inject services into.
     * @param member The name of the "virtual node".
     * @param protocolLookUp The "fiber" through which flow messaging will look up peers.
     * @param flowFactory A factory for constructing flows.
     * @param keystore The store for members' generated keys.
     */
    override fun injectServices(
        flow: Flow,
        member: MemberX500Name,
        fiber: SimFiber,
        flowFactory: FlowFactory,
        keyStore: SimKeyStore
    ) {
        log.info("Injecting services into ${flow.javaClass} for \"$member\"")
        checkAPIAvailability(flow, configuration)
        
        doInject(member, flow, JsonMarshallingService::class.java) { createJsonMarshallingService() }
        doInject(member, flow, FlowEngine::class.java) { createFlowEngine(configuration, member, fiber) }
        doInject(member, flow, FlowMessaging::class.java) {
            createFlowMessaging(configuration, flow, member, fiber, flowFactory)
        }
        doInject(member, flow, MemberLookup::class.java) { getOrCreateMemberLookup(member, fiber) }
        doInject(member, flow, SigningService::class.java) {
            getOrCreateSigningService(SimpleJsonMarshallingService(), keyStore)
        }
        doInject(member, flow, DigitalSignatureVerificationService::class.java) { createVerificationService() }
        doInject(member, flow, PersistenceService::class.java) { getOrCreatePersistenceService(member, fiber) }
        doInject(member, flow, SignatureSpecService::class.java) { createSpecService() }

        injectOtherCordaServices(flow, member)
    }

    private fun injectOtherCordaServices(flow: Flow, member: MemberX500Name) {
        configuration.serviceOverrides.keys.minus(availableAPIs).forEach {
            flow.injectIfRequired(it) {
                log.info("Injecting custom service for ${it.simpleName}")
                val serviceOverrideBuilder: ServiceOverrideBuilder<*> = configuration.serviceOverrides[it]!!
                serviceOverrideBuilder.buildService(member, flow.javaClass, null)!!
            }
        }
    }

    private fun <T> doInject(member: MemberX500Name, flow: Flow, serviceClass: Class<T>, builder: () -> T) {
        val resolvedBuilder: () -> Any = if (configuration.serviceOverrides.containsKey(serviceClass)) {{
            @Suppress("UNCHECKED_CAST")
            val serviceOverrideBuilder = configuration.serviceOverrides[serviceClass] as ServiceOverrideBuilder<T>
            serviceOverrideBuilder.buildService(member, flow::class.java, builder.invoke())?: error(
                "No override and no builder provided for Service $serviceClass, this should never happen"
            )
        }} else {{
            builder.invoke() ?: error(
                "No override and no builder provided for Service $serviceClass, this should never happen"
            )
        }}
        flow.injectIfRequired(serviceClass, resolvedBuilder)
    }

    private fun createSpecService() : SignatureSpecService {
        log.info("Injecting ${SignatureSpecService::class.java.simpleName}")
        return OnlyOneSignatureSpecService()
    }

    private fun createVerificationService(): DigitalSignatureVerificationService {
        log.info("Injecting ${DigitalSignatureVerificationService::class.java.simpleName}")
        return SimWithJsonSignatureVerificationService()
    }

    private fun getOrCreateSigningService(
        jsonMarshallingService: JsonMarshallingService,
        keyStore: SimKeyStore
    ): SigningService {
        log.info("Injecting ${SigningService::class.java.simpleName}")
        return SimWithJsonSigningService(jsonMarshallingService, keyStore)
    }

    private fun getOrCreateMemberLookup(member: MemberX500Name, fiber: SimFiber): MemberLookup {
        log.info("Injecting ${MemberLookup::class.java.simpleName}")
        return fiber.createMemberLookup(member)
    }

    private fun getOrCreatePersistenceService(member: MemberX500Name, fiber: SimFiber): PersistenceService  {
        log.info("Injecting ${PersistenceService::class.java.simpleName}")
        return fiber.getOrCreatePersistenceService(member)
    }

    private fun createJsonMarshallingService() : JsonMarshallingService {
        log.info("Injecting ${JsonMarshallingService::class.java.simpleName}")
        return SimpleJsonMarshallingService()
    }

    private fun createFlowEngine(
        configuration: SimulatorConfiguration,
        member: MemberX500Name,
        fiber: SimFiber
    ): FlowEngine {
        log.info("Injecting ${FlowEngine::class.java.simpleName}")
        return InjectingFlowEngine(configuration, member, fiber)
    }

    private fun createFlowMessaging(
        configuration: SimulatorConfiguration,
        flow: Flow,
        member: MemberX500Name,
        fiber: SimFiber,
        flowFactory: FlowFactory
    ): FlowMessaging {

        log.info("Injecting ${FlowMessaging::class.java.simpleName}")

        val flowClass = flow.javaClass
        val protocol = flowClass.getAnnotation(InitiatingFlow::class.java)?.protocol
            ?: flowClass.getAnnotation(InitiatedBy::class.java)?.protocol
            ?: throw NoProtocolAnnotationException(flowClass)

        return ConcurrentFlowMessaging(
            FlowContext(configuration, member, protocol),
            fiber,
            this,
            flowFactory
        )
    }
}


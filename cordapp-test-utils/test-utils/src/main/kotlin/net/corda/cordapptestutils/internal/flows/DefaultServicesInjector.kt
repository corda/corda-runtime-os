package net.corda.cordapptestutils.internal.flows

import net.corda.cordapptestutils.internal.messaging.ConcurrentFlowMessaging
import net.corda.cordapptestutils.internal.messaging.SimFiber
import net.corda.cordapptestutils.internal.tools.SimpleJsonMarshallingService
import net.corda.cordapptestutils.internal.utils.injectIfRequired
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name

/**
 * Injector for default services for Simulator.
 */
class DefaultServicesInjector : FlowServicesInjector {

    /**
     * Injects sensible default services into the provided flow. Currently injects:<br>
     * <ul>
     *   <li>JsonMarshallingService
     *   <li>FlowEngine
     *   <li>FlowMessaging
     * </ul>
     * As with the real Corda, injected service properties must be marked with the @CordaInject annotation.
     *
     * @flow The flow to inject services into
     * @member The name of the "virtual node"
     * @protocolLookUp The "fiber" through which flow messaging will look up peers
     * @flowFactory A factory for constructing flows
     */
    override fun injectServices(
        flow: Flow,
        member: MemberX500Name,
        fiber: SimFiber,
        flowFactory: FlowFactory,
    ) {
        val flowClass = flow.javaClass
        flow.injectIfRequired(JsonMarshallingService::class.java,
            createJsonMarshallingService())
        flow.injectIfRequired(FlowEngine::class.java,
            createFlowEngine(member, fiber))
        flow.injectIfRequired(FlowMessaging::class.java,
            createFlowMessaging(member, flowClass, fiber, flowFactory))
        flow.injectIfRequired(
            PersistenceService::class.java,
            getOrCreatePersistenceService(member, fiber))
    }

    private fun getOrCreatePersistenceService(member: MemberX500Name, fiber: SimFiber): PersistenceService  {
        return fiber.getOrCreatePersistenceService(member)
    }

    private fun createJsonMarshallingService() : JsonMarshallingService = SimpleJsonMarshallingService()
    private fun createFlowEngine(member: MemberX500Name, fiber: SimFiber): FlowEngine
        = InjectingFlowEngine(member, fiber)
    private fun createFlowMessaging(
        member: MemberX500Name,
        flowClass: Class<out Flow>,
        fiber: SimFiber,
        flowFactory: FlowFactory
    ): FlowMessaging = ConcurrentFlowMessaging(member, flowClass, fiber, this, flowFactory)
}


package net.corda.testutils.internal

import net.corda.testutils.services.DbPersistenceService
import net.corda.testutils.services.SimpleJsonMarshallingService
import net.corda.testutils.tools.injectIfRequired
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name

/**
 * Injector for default services for the CordaMock.
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
        fiberFake: FiberFake,
        flowFactory: FlowFactory,
    ) {
        val flowClass = flow.javaClass
        flow.injectIfRequired(JsonMarshallingService::class.java,
            createJsonMarshallingService())
        flow.injectIfRequired(FlowEngine::class.java,
            createFlowEngine(member, fiberFake))
        flow.injectIfRequired(FlowMessaging::class.java,
            createFlowMessaging(member, flowClass, fiberFake, flowFactory))
        flow.injectIfRequired(
            PersistenceService::class.java,
            createPersistenceService(member, fiberFake))
    }

    private fun createPersistenceService(member: MemberX500Name, fiberFake: FiberFake): PersistenceService  {
        val persistence = DbPersistenceService(member)
        fiberFake.registerPersistenceService(member, persistence)
        return persistence
    }

    private fun createJsonMarshallingService() : JsonMarshallingService = SimpleJsonMarshallingService()
    private fun createFlowEngine(member: MemberX500Name, fiberFake: FiberFake): FlowEngine
        = InjectingFlowEngine(member, fiberFake)
    private fun createFlowMessaging(
        member: MemberX500Name,
        flowClass: Class<out Flow>,
        fiberFake: FiberFake,
        flowFactory: FlowFactory
    ): FlowMessaging = ConcurrentFlowMessaging(member, flowClass, fiberFake, this, flowFactory)
}


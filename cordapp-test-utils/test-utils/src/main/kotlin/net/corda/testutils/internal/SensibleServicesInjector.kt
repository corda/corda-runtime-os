package net.corda.testutils.internal

import net.corda.testutils.services.SimpleJsonMarshallingService
import net.corda.testutils.tools.injectIfRequired
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.types.MemberX500Name

/**
 * Injector for default services for the CordaMock.
 */
class SensibleServicesInjector : FlowServicesInjector {

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
     * @x500 The name of the "virtual node"
     * @protocolLookUp The "fiber" through which flow messaging will look up peers
     * @flowFactory A factory for constructing flows
     */
    override fun injectServices(
        flow: Flow,
        x500: MemberX500Name,
        protocolLookUp: ProtocolLookUp,
        flowFactory: FlowFactory,
    ) {
        val flowClass = flow.javaClass
        flow.injectIfRequired(JsonMarshallingService::class.java,
            createJsonMarshallingService())
        flow.injectIfRequired(FlowEngine::class.java,
            createFlowEngine(x500, protocolLookUp))
        flow.injectIfRequired(FlowMessaging::class.java,
            createFlowMessaging(x500, flowClass, protocolLookUp, flowFactory))
    }

    private fun createJsonMarshallingService() : JsonMarshallingService = SimpleJsonMarshallingService()
    private fun createFlowEngine(x500: MemberX500Name, protocolLookUp: ProtocolLookUp): FlowEngine = InjectingFlowEngine(
        x500,
        protocolLookUp,
        this
    )
    private fun createFlowMessaging(
        x500: MemberX500Name,
        flowClass: Class<out Flow>,
        protocolLookUp: ProtocolLookUp,
        flowFactory: FlowFactory
    ): FlowMessaging = ConcurrentFlowMessaging(x500, flowClass, protocolLookUp, this, flowFactory)
}


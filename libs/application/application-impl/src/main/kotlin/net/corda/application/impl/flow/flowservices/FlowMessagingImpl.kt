package net.corda.application.impl.flow.flowservices

import net.corda.application.internal.flow.session.FlowSessionFactory
import net.corda.flow.manager.fiber.FlowFiberService
import net.corda.v5.application.flows.FlowSession
import net.corda.v5.application.flows.UntrustworthyData
import net.corda.v5.application.flows.flowservices.FlowMessaging
import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import java.util.UUID

@Component(service = [FlowMessaging::class, SingletonSerializeAsToken::class], scope = ServiceScope.PROTOTYPE)
class FlowMessagingImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService,
    @Reference(service = FlowSessionFactory::class)
    private val flowSessionFactory: FlowSessionFactory
) : FlowMessaging, SingletonSerializeAsToken, CordaFlowInjectable {

    @Suspendable
    override fun close(sessions: Set<FlowSession>) {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun initiateFlow(x500Name: MemberX500Name): FlowSession {
        val sessionId = UUID.randomUUID().toString()
        return flowSessionFactory.create(sessionId, x500Name, initiated = false).also {
            flowFiberService
                .getExecutingFiber()
                .getExecutionContext()
                .flowStackService
                .peek()
                ?.sessionIds
                ?.add(sessionId)
        }
    }

    @Suspendable
    override fun <R> receiveAll(receiveType: Class<out R>, sessions: Set<FlowSession>): List<UntrustworthyData<R>> {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun receiveAllMap(sessions: Map<FlowSession, Class<out Any>>): Map<FlowSession, UntrustworthyData<Any>> {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun sendAll(payload: Any, sessions: Set<FlowSession>) {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun sendAllMap(payloadsPerSession: Map<FlowSession, *>) {
        TODO("Not yet implemented")
    }
}
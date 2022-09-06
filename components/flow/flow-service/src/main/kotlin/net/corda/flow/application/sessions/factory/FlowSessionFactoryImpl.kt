package net.corda.flow.application.sessions.factory

import java.security.AccessController
import java.security.PrivilegedActionException
import java.security.PrivilegedExceptionAction
import net.corda.flow.application.sessions.FlowSessionImpl
import net.corda.flow.fiber.FlowFiberSerializationService
import net.corda.flow.fiber.FlowFiberService
import net.corda.v5.application.messaging.FlowContextPropertiesMutator
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.types.MemberX500Name
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowSessionFactory::class])
class FlowSessionFactoryImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService,
    @Reference(service = FlowFiberSerializationService::class)
    private val flowFiberSerializationService: FlowFiberSerializationService
) : FlowSessionFactory {

    override fun createInitiatedFlowSession(
        sessionId: String,
        x500Name: MemberX500Name,
        contextProperties: Map<String, String>
    ): FlowSession {
        return try {
            AccessController.doPrivileged(PrivilegedExceptionAction {
                FlowSessionImpl.asInitiatedSession(
                    counterparty = x500Name,
                    sessionId,
                    flowFiberService,
                    flowFiberSerializationService,
                    contextProperties
                )
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
    }

    override fun createInitiatingFlowSession(
        sessionId: String,
        x500Name: MemberX500Name,
        flowContextPropertiesMutator: FlowContextPropertiesMutator?
    ): FlowSession {
        return try {
            AccessController.doPrivileged(PrivilegedExceptionAction {
                FlowSessionImpl.asInitiatingSession(
                    counterparty = x500Name,
                    sessionId,
                    flowFiberService,
                    flowFiberSerializationService,
                    flowContextPropertiesMutator
                )
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
    }
}
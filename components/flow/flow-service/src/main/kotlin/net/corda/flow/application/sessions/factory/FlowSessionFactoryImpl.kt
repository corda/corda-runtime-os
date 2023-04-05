package net.corda.flow.application.sessions.factory

import net.corda.flow.application.serialization.SerializationServiceInternal
import net.corda.flow.application.sessions.impl.FlowSessionImpl
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.state.impl.FlatSerializableContext
import net.corda.flow.state.impl.MutableFlatSerializableContext
import net.corda.v5.application.messaging.FlowContextPropertiesBuilder
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.types.MemberX500Name
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.security.AccessController
import java.security.PrivilegedActionException
import java.security.PrivilegedExceptionAction

@Component(service = [FlowSessionFactory::class])
class FlowSessionFactoryImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService,
    @Reference(service = SerializationServiceInternal::class)
    private val serializationService: SerializationServiceInternal
) : FlowSessionFactory {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun createInitiatedFlowSession(
        sessionId: String,
        x500Name: MemberX500Name,
        contextProperties: Map<String, String>,
        isInteropSession: Boolean
    ): FlowSession {
        log.info("createInitiatedFlowSession sessionId=$sessionId, counterparty=$x500Name")
        return try {
            AccessController.doPrivileged(PrivilegedExceptionAction {
                FlowSessionImpl(
                    counterparty = x500Name,
                    sessionId,
                    isInteropSession,
                    flowFiberService,
                    serializationService,
                    FlatSerializableContext(
                        contextUserProperties = emptyMap(),
                        contextPlatformProperties = contextProperties
                    ),
                    FlowSessionImpl.Direction.INITIATED_SIDE
                )
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
    }

    override fun createInitiatingFlowSession(
        sessionId: String,
        x500Name: MemberX500Name,
        flowContextPropertiesBuilder: FlowContextPropertiesBuilder?,
        isInteropSession: Boolean
    ): FlowSession {
        return try {
            AccessController.doPrivileged(PrivilegedExceptionAction {
                FlowSessionImpl(
                    counterparty = x500Name,
                    sessionId,
                    isInteropSession,
                    flowFiberService,
                    serializationService,
                    createInitiatingFlowContextProperties(
                        flowContextPropertiesBuilder,
                        flowFiberService
                    ),
                    FlowSessionImpl.Direction.INITIATING_SIDE
                )
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
    }

    private fun createInitiatingFlowContextProperties(
        flowContextPropertiesBuilder: FlowContextPropertiesBuilder?,
        flowFiberService: FlowFiberService,
    ) = flowFiberService.getExecutingFiber().getExecutionContext().flowCheckpoint.flowContext.let { flowContext ->
        // Initiating session always created in a running flow
        flowContextPropertiesBuilder?.let { contextBuilder ->
            // Snapshot the current flow context
            MutableFlatSerializableContext(
                contextUserProperties = flowContext.flattenUserProperties(),
                contextPlatformProperties = flowContext.flattenPlatformProperties()
            ).also { mutableContext ->
                // Let the builder modify the context
                contextBuilder.apply(mutableContext)
            }.let { mutableContext ->
                // Turn the mutable context into an immutable one for the public api
                FlatSerializableContext(
                    contextUserProperties = mutableContext.flattenUserProperties(),
                    contextPlatformProperties = mutableContext.flattenPlatformProperties()
                )
            }
        } ?:
        // No context builder passed, snapshot the current flow context
        FlatSerializableContext(
            contextUserProperties = flowContext.flattenUserProperties(),
            contextPlatformProperties = flowContext.flattenPlatformProperties()
        )
    }
}
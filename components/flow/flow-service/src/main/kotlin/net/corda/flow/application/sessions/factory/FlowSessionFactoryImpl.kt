package net.corda.flow.application.sessions.factory

import net.corda.flow.application.serialization.FlowSerializationService
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
import java.security.PrivilegedActionException
import java.security.PrivilegedExceptionAction
import java.time.Duration

@Component(service = [FlowSessionFactory::class])
class FlowSessionFactoryImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService,
    @Reference(service = FlowSerializationService::class)
    private val serializationService: FlowSerializationService
) : FlowSessionFactory {

    override fun createInitiatedFlowSession(
        sessionId: String,
        requireClose: Boolean,
        sessionTimeout: Duration?,
        x500Name: MemberX500Name,
        contextProperties: Map<String, String>
    ): FlowSession {
        return try {
            @Suppress("deprecation", "removal")
            java.security.AccessController.doPrivileged(PrivilegedExceptionAction {
                FlowSessionImpl(
                    counterparty = x500Name,
                    sessionId,
                    flowFiberService,
                    serializationService,
                    FlatSerializableContext(
                        contextUserProperties = emptyMap(),
                        contextPlatformProperties = contextProperties
                    ),
                    FlowSessionImpl.Direction.INITIATED_SIDE,
                    requireClose,
                    sessionTimeout,
                )
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
    }

    override fun createInitiatingFlowSession(
        sessionId: String,
        requireClose: Boolean,
        sessionTimeout: Duration?,
        x500Name: MemberX500Name,
        flowContextPropertiesBuilder: FlowContextPropertiesBuilder?
    ): FlowSession {
        return try {
            @Suppress("deprecation", "removal")
            java.security.AccessController.doPrivileged(PrivilegedExceptionAction {
                FlowSessionImpl(
                    counterparty = x500Name,
                    sessionId,
                    flowFiberService,
                    serializationService,
                    createInitiatingFlowContextProperties(
                        flowContextPropertiesBuilder,
                        flowFiberService
                    ),
                    FlowSessionImpl.Direction.INITIATING_SIDE,
                    requireClose,
                    sessionTimeout,
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
package net.corda.flow.external.events.impl.executor

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.UUID

@Component(service = [ExternalEventExecutor::class, SingletonSerializeAsToken::class])
class ExternalEventExecutorImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService,
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService
) : ExternalEventExecutor, SingletonSerializeAsToken {

    @Suspendable
    override fun <PARAMETERS : Any, RESPONSE : Any, RESUME> execute(
        factoryClass: Class<out ExternalEventFactory<PARAMETERS, RESPONSE, RESUME>>,
        parameters: PARAMETERS
    ): RESUME {
        // `requestId` is a deterministic ID per event which allows us to achieve idempotency by de-duplicating events processing;
        //  A deterministic ID is required so that events replayed from the flow engine won't be reprocessed on the consumer-side.
        val uuid = deterministicUUID(parameters)

        @Suppress("unchecked_cast")
        return with(flowFiberService.getExecutingFiber()) {
            suspend(
                FlowIORequest.ExternalEvent(
                    generateRequestId(uuid, this),
                    factoryClass,
                    parameters,
                    externalContext(this)
                )
            )
        } as RESUME
    }

    private fun externalContext(flowFiber: FlowFiber): Map<String, String> =
        with(flowFiber.getExecutionContext().flowCheckpoint.flowContext) {
            localToExternalContextMapper(
                userContextProperties = this.flattenUserProperties(),
                platformContextProperties = this.flattenPlatformProperties()
            )
        }

    private fun <PARAMETERS : Any> deterministicUUID(parameters: PARAMETERS): UUID {
        return UUID.nameUUIDFromBytes(serializationService.serialize(parameters).bytes)
    }

    private fun generateRequestId(uuid: UUID, flowFiber: FlowFiber): String {
        val flowCheckpoint = flowFiber
            .getExecutionContext()
            .flowCheckpoint

        val flowId = flowCheckpoint.flowId
        val suspendCount = flowCheckpoint.suspendCount

        return UUID
            .nameUUIDFromBytes("$flowId-$uuid-$suspendCount".toByteArray())
            .toString()
    }
}

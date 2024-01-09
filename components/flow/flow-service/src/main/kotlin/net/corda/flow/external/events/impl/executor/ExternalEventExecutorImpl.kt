package net.corda.flow.external.events.impl.executor

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.nio.ByteBuffer
import java.util.UUID

@Component(service = [ExternalEventExecutor::class, SingletonSerializeAsToken::class])
class ExternalEventExecutorImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService
) : ExternalEventExecutor, SingletonSerializeAsToken {

    @Suspendable
    override fun <PARAMETERS : Any, RESPONSE : Any, RESUME> execute(
        factoryClass: Class<out ExternalEventFactory<PARAMETERS, RESPONSE, RESUME>>,
        parameters: PARAMETERS
    ): RESUME {
        // `requestId` is a deterministic ID per event which allows us to achieve idempotency by de-duplicating events processing;
        //  A deterministic ID is required so that events replayed from the flow engine won't be reprocessed on the consumer-side.
        val uuid = deterministicUUID(parameters)
        val requestId = generateRequestId(uuid)

        @Suppress("unchecked_cast")
        return with(flowFiberService.getExecutingFiber()) {
            suspend(
                FlowIORequest.ExternalEvent(
                    requestId,
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
        // A UUID based on the entropy of the hashcode isn't as robust as serializing the object,
        // but we can't guarantee that [PARAMETERS] is a serializable type.
        val byteBuffer = ByteBuffer.wrap(ByteArray(8))
        byteBuffer.putLong(0, parameters.hashCode().toLong())
        return UUID.nameUUIDFromBytes(byteBuffer.array())
    }

    private fun generateRequestId(uuid: UUID): String {
        val flowCheckpoint = flowFiberService
            .getExecutingFiber()
            .getExecutionContext()
            .flowCheckpoint

        val flowId = flowCheckpoint.flowId
        val suspendCount = flowCheckpoint.suspendCount

        return UUID
            .nameUUIDFromBytes("$flowId-$uuid-$suspendCount".toByteArray())
            .toString()
    }
}

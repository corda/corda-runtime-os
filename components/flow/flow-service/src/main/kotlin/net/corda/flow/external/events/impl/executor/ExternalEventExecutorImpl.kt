package net.corda.flow.external.events.impl.executor

import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.flow.application.serialization.SerializationServiceInternal
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ExternalEventExecutor::class, SingletonSerializeAsToken::class])
class ExternalEventExecutorImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService,
    @Reference(service = SerializationServiceInternal::class)
    private val serializationService: SerializationServiceInternal,
    @Reference(service = PlatformDigestService::class)
    private val platformDigestService: PlatformDigestService
) : ExternalEventExecutor, SingletonSerializeAsToken {

    @Suspendable
    override fun <PARAMETERS : Any, RESPONSE : Any, RESUME> execute(
        factoryClass: Class<out ExternalEventFactory<PARAMETERS, RESPONSE, RESUME>>,
        parameters: PARAMETERS
    ): RESUME {
        // `requestId` is a deterministic ID per event which allows us to achieve idempotency by de-duplicating events processing;
        //  A deterministic ID is required so that events replayed from the flow engine won't be reprocessed on the consumer-side.
        val uuid = deterministicBytesID(parameters)

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

    private fun <PARAMETERS : Any> deterministicBytesID(parameters: PARAMETERS): String {
        val bytes = serializationService.serialize(parameters).bytes
        val hash = platformDigestService.hash(bytes, DigestAlgorithmName.SHA2_256)
        return hash.toHexString()
    }

    private fun generateRequestId(bytesAsHexString: String, flowFiber: FlowFiber): String {
        val flowCheckpoint = flowFiber
            .getExecutionContext()
            .flowCheckpoint

        val flowId = flowCheckpoint.flowId
        val suspendCount = flowCheckpoint.suspendCount

        return "$flowId-$bytesAsHexString-$suspendCount"
    }
}

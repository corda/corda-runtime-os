package net.corda.flow.application.persistence.external.events

import net.corda.crypto.cipher.suite.sha256Bytes
import net.corda.data.persistence.PersistEntities
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.fiber.FlowFiberService
import net.corda.v5.base.util.EncodingUtils.toBase64
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.nio.ByteBuffer

@Component(service = [ExternalEventFactory::class])
class PersistExternalEventFactory @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService
) : AbstractPersistenceExternalEventFactory<PersistParameters>() {

    override fun createRequest(parameters: PersistParameters): Any {
        val deterministicID = getDeduplicationId(parameters.serializedEntities)
        return PersistEntities(parameters.serializedEntities, deterministicID)
    }

    /**
     * Get deterministic id to be used by entity persistence worker to deduplicate requests when a fiber is re-run
     */
    private fun getDeduplicationId(serializedEntities: List<ByteBuffer>): String {
        val checkpoint = flowFiberService.getExecutingFiber().getExecutionContext().flowCheckpoint
        return toBase64("${checkpoint.flowId}${checkpoint.suspendCount}${serializedEntities.hashCode()}".toByteArray().sha256Bytes())
    }
}

data class PersistParameters(val serializedEntities: List<ByteBuffer>)
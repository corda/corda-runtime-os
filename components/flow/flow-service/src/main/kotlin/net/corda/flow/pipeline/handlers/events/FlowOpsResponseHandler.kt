package net.corda.flow.pipeline.handlers.events

import net.corda.crypto.manager.CryptoManager
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.flow.pipeline.FlowEventContext
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowEventHandler::class])
class FlowOpsResponseHandler @Activate constructor(
    @Reference(service = CryptoManager::class)
    private val cryptoManager: CryptoManager
) : FlowEventHandler<FlowOpsResponse> {

    private companion object {
        val log = contextLogger()
    }

    override val type = FlowOpsResponse::class.java

    override fun preProcess(context: FlowEventContext<FlowOpsResponse>): FlowEventContext<FlowOpsResponse> {
        val checkpoint = context.checkpoint
        val flowOpsResponse = context.inputEventPayload
        log.debug { "Crypto response received. Id ${flowOpsResponse.context.requestId}, result: ${flowOpsResponse.response::class.java}" }
        val cryptoState = checkpoint.cryptoState
        if (cryptoState == null) {
            //Duplicate response
            log.warn("Received response but cryptoState in checkpoint was null. requestId ${flowOpsResponse.context.requestId}")
        } else {
            checkpoint.cryptoState = cryptoManager.processMessageReceived(cryptoState, flowOpsResponse)
        }
        return context
    }
}
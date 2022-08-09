package net.corda.flow.pipeline.handlers.requests

import net.corda.crypto.flow.CryptoFlowOpsTransformer
import net.corda.crypto.manager.CryptoManager
import net.corda.data.flow.state.waiting.SignedBytes
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowRequestHandler::class])
class SignBytesRequestHandler @Activate constructor(
    @Reference(service = CryptoFlowOpsTransformer::class)
    private val cryptoFlowOpsTransformer: CryptoFlowOpsTransformer,
    @Reference(service = CryptoManager::class)
    private val cryptoManager: CryptoManager
) : FlowRequestHandler<FlowIORequest.SignBytes> {

    override val type = FlowIORequest.SignBytes::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.SignBytes): WaitingFor {
        return WaitingFor(SignedBytes(request.requestId))
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.SignBytes): FlowEventContext<Any> {
        val flowOpsRequest = cryptoFlowOpsTransformer.createSign(
            requestId = request.requestId,
            tenantId = context.checkpoint.holdingIdentity.shortHash.value,
            publicKey = request.publicKey,
            signatureSpec = request.signatureSpec,
            data = request.bytes,
            context = emptyMap()
        )

        context.checkpoint.cryptoState = cryptoManager.processMessageToSend(request.requestId, flowOpsRequest)

        return context
    }
}

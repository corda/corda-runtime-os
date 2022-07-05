package net.corda.crypto.manager.impl

import net.corda.crypto.manager.CryptoManager
import net.corda.data.crypto.wire.ops.flow.FlowOpsRequest
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.data.flow.state.crypto.CryptoState
import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.FlowConfig.CRYPTO_MESSAGE_RESEND_WINDOW
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Component
import java.time.Instant

@Component(service = [CryptoManager::class])
class CryptoManagerImpl : CryptoManager {

    private companion object {
        val logger = contextLogger()
        const val INSTANT_COMPARE_BUFFER = 100L
    }

    override fun processMessageToSend(requestId: String, request: FlowOpsRequest): CryptoState {
        logger.debug { "Processing crypto request of type ${request.request::class} with id $requestId "}
        return CryptoState.newBuilder()
            .setRequest(request)
            .setRequestId(requestId)
            .setRetries(0)
            .setResponse(null)
            .setSendTimestamp(request.context.requestTimestamp)
            .build()
    }

    override fun processMessageReceived(cryptoState: CryptoState, response: FlowOpsResponse): CryptoState {
        val requestId = response.context.requestId
        logger.debug { "Processing received crypto response of status ${response.response::class.java.name} with id $requestId "}
        if (requestId == cryptoState.requestId) {
            logger.debug { "Crypto response with id $requestId matched last sent request" }
            cryptoState.response = response
        }
        return cryptoState
    }

    override fun getMessageToSend(cryptoState: CryptoState, instant: Instant, config: SmartConfig): Pair<CryptoState, FlowOpsRequest?> {
        val request = cryptoState.request
        val waitingForResponse = request != null && cryptoState.response == null
        val requestSendWindowValid = cryptoState.sendTimestamp.toEpochMilli() < (instant.toEpochMilli() + INSTANT_COMPARE_BUFFER)
        return if (waitingForResponse && requestSendWindowValid) {
            logger.debug { "Resending crypto request which was last sent at ${request.context.requestTimestamp}"}
            cryptoState.sendTimestamp = instant.plusMillis(config.getLong(CRYPTO_MESSAGE_RESEND_WINDOW))
            request.context.requestTimestamp = instant
            Pair(cryptoState, request)
        } else {
            Pair(cryptoState, null)
        }
    }
}
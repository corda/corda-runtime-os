package net.corda.flow.persistence.manager.impl

import java.time.Instant
import net.corda.data.flow.state.persistence.PersistenceState
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.EntityResponse
import net.corda.flow.persistence.manager.PersistenceManager
import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.FlowConfig.PERSISTENCE_MESSAGE_RESEND_WINDOW
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Component

@Component(service = [PersistenceManager::class])
class PersistenceManagerImpl : PersistenceManager {

    private companion object {
        val logger = contextLogger()
        const val INSTANT_COMPARE_BUFFER = 100L
    }

    override fun processMessageToSend(requestId: String, request: EntityRequest): PersistenceState {
        logger.debug { "Processing query request of type ${request.request::class} with id $requestId " }
        return PersistenceState.newBuilder()
            .setRequest(request)
            .setRequestId(requestId)
            .setRetries(0)
            .setResponse(null)
            .setSendTimestamp(request.timestamp)
            .build()
    }

    override fun processMessageReceived(persistenceState: PersistenceState, response: EntityResponse): PersistenceState {
        logger.debug { "Processing received query response of status ${response.responseType::class} with id ${response.requestId} " }
        if (response.requestId == persistenceState.requestId) {
            logger.debug { "Response with id ${response.requestId} matched last sent request" }
            persistenceState.response = response
        }
        return persistenceState
    }

    override fun getMessageToSend(
        persistenceState: PersistenceState,
        instant: Instant,
        config: SmartConfig
    ): Pair<PersistenceState, EntityRequest?> {
        val request = persistenceState.request
        val waitingForResponse = request != null && persistenceState.response == null
        val requestSendWindowValid = persistenceState.sendTimestamp.toEpochMilli() < (instant.toEpochMilli() + INSTANT_COMPARE_BUFFER)
        return if (waitingForResponse && requestSendWindowValid) {
            logger.debug { "Resending query message which was last sent at ${request.timestamp}" }
            persistenceState.sendTimestamp = instant.plusMillis(config.getLong(PERSISTENCE_MESSAGE_RESEND_WINDOW))
            request.timestamp = instant
            Pair(persistenceState, request)
        } else {
            Pair(persistenceState, null)
        }
    }
}

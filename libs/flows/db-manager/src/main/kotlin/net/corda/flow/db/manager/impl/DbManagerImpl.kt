package net.corda.flow.db.manager.impl

import java.time.Instant
import net.corda.data.flow.state.db.Query
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.EntityResponse
import net.corda.flow.db.manager.DbManager
import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.FlowConfig.DB_MESSAGE_RESEND_WINDOW
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Component

@Component(service = [DbManager::class])
class DbManagerImpl : DbManager {

    private companion object {
        val logger = contextLogger()
        const val INSTANT_COMPARE_BUFFER = 100L
    }

    override fun processMessageToSend(requestId: String, request: EntityRequest): Query {
        logger.debug { "Processing query request of type ${request.request::class} with id $requestId "}
        return Query.newBuilder()
            .setRequest(request)
            .setRequestId(requestId)
            .setRetries(0)
            .setResponse(null)
            .setSendTimestamp(request.timestamp)
            .build()
    }

    override fun processMessageReceived(query: Query, response: EntityResponse): Query {
        logger.debug { "Processing received query response of status ${response.responseType::class} with id ${response.requestId} "}
        if (response.requestId == query.requestId) {
            logger.debug("Query response matched last sent request")
            query.response = response
        }
        return query
    }

    override fun getMessageToSend(query: Query, instant: Instant, config: SmartConfig): Pair<Query, EntityRequest?> {
        val request = query.request
        return if (request != null && query.sendTimestamp.toEpochMilli() < (instant.toEpochMilli() + INSTANT_COMPARE_BUFFER)) {
            logger.debug { "Resending query message which was last sent at ${request.timestamp}"}
            query.sendTimestamp = instant.plusMillis(config.getLong(DB_MESSAGE_RESEND_WINDOW))
            request.timestamp = instant
            Pair(query, request)
        } else {
            Pair(query, null)
        }
    }
}

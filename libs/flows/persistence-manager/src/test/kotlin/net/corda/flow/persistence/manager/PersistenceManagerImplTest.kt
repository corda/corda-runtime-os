package net.corda.flow.persistence.manager

import com.typesafe.config.ConfigValueFactory
import java.nio.ByteBuffer
import java.time.Instant
import net.corda.data.flow.state.persistence.PersistenceState
import net.corda.data.identity.HoldingIdentity
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.EntityResponse
import net.corda.data.persistence.EntityResponseSuccess
import net.corda.data.persistence.PersistEntity
import net.corda.flow.persistence.manager.impl.PersistenceManagerImpl
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.schema.configuration.FlowConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PersistenceManagerImplTest {

    private companion object {
        const val persistTimeStampMilli = 1000000L
        const val resendWindow = 5000L
        const val requestId = "RequestId1"
        val flowConfig: SmartConfig = SmartConfigImpl.empty()
            .withValue(FlowConfig.PERSISTENCE_MESSAGE_RESEND_WINDOW, ConfigValueFactory.fromAnyRef(resendWindow))
            .withValue(FlowConfig.PERSISTENCE_RESEND_BUFFER, ConfigValueFactory.fromAnyRef(100L))

        val persistRequest: EntityRequest = EntityRequest.newBuilder()
            .setRequest(PersistEntity(ByteBuffer.wrap("bytes".toByteArray())))
            .setTimestamp(Instant.ofEpochMilli(persistTimeStampMilli))
            .setFlowId("flowId")
            .setHoldingIdentity(HoldingIdentity("Alice", "Group1"))
            .build()
    }

    private val persistenceManager = PersistenceManagerImpl()


    @Test
    fun `processing a new query`() {
        val query = persistenceManager.processMessageToSend(requestId, persistRequest)
        assertThat(query.retries).isEqualTo(0)
        assertThat(query.request).isEqualTo(persistRequest)
        assertThat(query.requestId).isEqualTo(requestId)
        assertThat(query.sendTimestamp).isEqualTo(persistRequest.timestamp)
        assertThat(query.response).isNull()
    }

    @Test
    fun `processing a valid response for a query`() {
        val query = PersistenceState.newBuilder()
            .setRequest(persistRequest)
            .setResponse(null)
            .setSendTimestamp(persistRequest.timestamp)
            .setRequestId(requestId)
            .build()

        val responseSuccess = EntityResponse.newBuilder()
            .setResponseType(EntityResponseSuccess())
            .setRequestId(requestId)
            .setTimestamp(Instant.now())
            .build()

        val updatedQuery = persistenceManager.processMessageReceived(query, responseSuccess)
        assertThat(updatedQuery.response).isEqualTo(responseSuccess)
    }

    @Test
    fun `processing an invalid response for the query`() {
        val query = PersistenceState.newBuilder()
            .setRequest(persistRequest)
            .setResponse(null)
            .setSendTimestamp(persistRequest.timestamp)
            .setRequestId(requestId)
            .build()

        val responseSuccess = EntityResponse.newBuilder()
            .setResponseType(EntityResponseSuccess())
            .setRequestId("invalidId")
            .setTimestamp(Instant.now())
            .build()

        val updatedQuery = persistenceManager.processMessageReceived(query, responseSuccess)
        assertThat(updatedQuery.response).isNull()
    }

    @Test
    fun `get message to send for a query`() {
        val query = PersistenceState.newBuilder()
            .setRequest(persistRequest)
            .setResponse(null)
            .setSendTimestamp(persistRequest.timestamp)
            .setRequestId(requestId)
            .build()

        val (updatedQuery, request) = persistenceManager.getMessageToSend(query, Instant.ofEpochMilli(persistTimeStampMilli), flowConfig)
        assertThat(request).isEqualTo(persistRequest)
        assertThat(updatedQuery.sendTimestamp.toEpochMilli()).isEqualTo(persistRequest.timestamp.toEpochMilli() + resendWindow)
    }

    @Test
    fun `get message to send for a query which already has a response`() {
        val responseSuccess = EntityResponse.newBuilder()
            .setResponseType(EntityResponseSuccess())
            .setRequestId(requestId)
            .setTimestamp(Instant.now())
            .build()

        val query = PersistenceState.newBuilder()
            .setRequest(persistRequest)
            .setResponse(responseSuccess)
            .setSendTimestamp(persistRequest.timestamp)
            .setRequestId(requestId)
            .build()

        val (_, request) = persistenceManager.getMessageToSend(query, Instant.ofEpochMilli(persistTimeStampMilli), flowConfig)
        assertThat(request).isNull()
    }

    @Test
    fun `no message to send for a query due to resend window not reached`() {
        val query = PersistenceState.newBuilder()
            .setRequest(persistRequest)
            .setResponse(null)
            .setSendTimestamp(persistRequest.timestamp)
            .setRequestId(requestId)
            .build()

        val (updatedQuery, request) = persistenceManager.getMessageToSend(
            query,
            Instant.ofEpochMilli(persistTimeStampMilli - 10000L),
            flowConfig
        )
        assertThat(request).isNull()
        assertThat(updatedQuery.sendTimestamp.toEpochMilli()).isEqualTo(persistRequest.timestamp.toEpochMilli())
    }
}
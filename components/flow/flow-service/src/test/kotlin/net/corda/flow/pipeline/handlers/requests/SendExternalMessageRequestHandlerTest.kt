package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.flow.state.waiting.Wakeup as WakeupState
import net.corda.external.messaging.entities.VerifiedRoute
import net.corda.external.messaging.services.ExternalMessagingRecordFactory
import net.corda.external.messaging.services.ExternalMessagingRoutingService
import net.corda.flow.RequestHandlerTestContext
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.exceptions.FlowPlatformException
import net.corda.libs.external.messaging.entities.InactiveResponseType
import net.corda.libs.external.messaging.entities.Route
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SendExternalMessageRequestHandlerTest {

    private val externalMessagingRoutingService = mock<ExternalMessagingRoutingService>()
    private val externalMessagingRecordFactory = mock<ExternalMessagingRecordFactory>()

    private val flowChannelName = "ch1"
    private val flowMessageId = "mid1"
    private val flowMessage = "m1"
    private val externalReceiveTopicName = "topic1"

    private val testContext = RequestHandlerTestContext(Any())
    private val holdingIdShortHash = testContext.flowCheckpoint.holdingIdentity.shortHash.toString()
    private val ioRequest = FlowIORequest.SendExternalMessage(flowChannelName, flowMessageId, flowMessage)
    private val handler = SendExternalMessageRequestHandler(
        externalMessagingRoutingService,
        externalMessagingRecordFactory,
        testContext.flowRecordFactory
    )

    @Test
    fun `Handler is waiting for wakeup`() {
        assertThat(handler.getUpdatedWaitingFor(testContext.flowEventContext, ioRequest))
            .isEqualTo(WaitingFor(WakeupState()))
    }

    @Test
    fun `Route not found should throw platform exception`() {
        whenever(externalMessagingRoutingService.getRoute(holdingIdShortHash, flowChannelName)).thenReturn(null)

        val error = assertThrows<FlowPlatformException> {
            handler.postProcess(testContext.flowEventContext, ioRequest)
        }

        assertThat(error.message).isEqualTo("Failed to send message, no route found for channel='${flowChannelName}'.")
    }

    @Suppress("MaxLineLength")
    @Test
    fun `Route topic does not exist throws platform exception`() {
        val route = createVerifiedRoute(true, InactiveResponseType.IGNORE, false)
        whenever(externalMessagingRoutingService.getRoute(holdingIdShortHash, flowChannelName)).thenReturn(route)

        val error = assertThrows<FlowPlatformException> {
            handler.postProcess(testContext.flowEventContext, ioRequest)
        }

        assertThat(error.message)
            .isEqualTo(
                "Failed to send message, topic '${externalReceiveTopicName}' does not exist for channel '${flowChannelName}'."
            )
    }

    @Test
    fun `Route inactive + inactive response error should throw platform exception`() {
        val route = createVerifiedRoute(false, InactiveResponseType.ERROR, true)
        whenever(externalMessagingRoutingService.getRoute(holdingIdShortHash, flowChannelName)).thenReturn(route)

        val error = assertThrows<FlowPlatformException> {
            handler.postProcess(testContext.flowEventContext, ioRequest)
        }

        assertThat(error.message)
            .isEqualTo(
                "Failed to send message, channel='${flowChannelName}' is inactive."
            )
    }

    @Test
    fun `Route inactive + ignore should return only wakeup event`() {
        val route = createVerifiedRoute(false, InactiveResponseType.IGNORE, true)
        whenever(externalMessagingRoutingService.getRoute(holdingIdShortHash, flowChannelName)).thenReturn(route)

        val result = handler.postProcess(testContext.flowEventContext, ioRequest)

        assertThat(result.outputRecords).isEmpty()
    }

    @Test
    fun `Should return only wakeup event and external message `() {
        val verifiedRoute = createVerifiedRoute(true, InactiveResponseType.ERROR, true)
        val messageRecord = Record("", "", "")

        whenever(externalMessagingRecordFactory.createSendRecord(any(), any(), any(), any())).thenReturn(messageRecord)
        whenever(externalMessagingRoutingService.getRoute(holdingIdShortHash, flowChannelName))
            .thenReturn(verifiedRoute)

        val result = handler.postProcess(testContext.flowEventContext, ioRequest)

        assertThat(result.outputRecords).containsOnly(messageRecord)
        verify(externalMessagingRecordFactory)
            .createSendRecord(
                holdingIdShortHash,
                verifiedRoute.route,
                flowMessageId,
                flowMessage
            )
    }

    private fun createVerifiedRoute(
        active: Boolean,
        inactiveResponseType: InactiveResponseType,
        isVerified: Boolean
    ): VerifiedRoute {
        return VerifiedRoute(
            Route(flowChannelName, externalReceiveTopicName, active, inactiveResponseType),
            isVerified
        )
    }
}

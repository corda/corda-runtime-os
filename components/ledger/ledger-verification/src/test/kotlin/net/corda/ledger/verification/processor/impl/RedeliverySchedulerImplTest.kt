package net.corda.ledger.verification.processor.impl

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.ledger.utxo.contract.verification.VerifyContractsRequest
import net.corda.ledger.utxo.contract.verification.VerifyContractsRequestRedelivery
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.concurrent.ScheduledExecutorService

class RedeliverySchedulerImplTest {
    private companion object {
        const val DELAY = 1000L
    }

    private val publisherFactory = mock<PublisherFactory>()
    private val config = mock<Map<String, SmartConfig>>()
    private val publisher = mock<Publisher>()
    private val scheduledExecutorService = mock<ScheduledExecutorService>()
    private val redeliveryScheduler = RedeliverySchedulerImpl(publisherFactory, scheduledExecutorService)

    @BeforeEach
    fun setup() {
        whenever(publisherFactory.createPublisher(any(), any())).thenReturn(publisher)
        whenever(config[MESSAGING_CONFIG]).thenReturn(mock())
        redeliveryScheduler.onConfigChange(config)
    }

    @Test
    fun `schedules redeliveries on post commit`() {
        val requestId1 = "requestId1"
        val requestId2 = "requestId2"
        val request2 = createRequest(requestId2)
        val timestamp = Instant.now()
        val scheduleDelivery = timestamp.plusMillis(DELAY)
        val updatedStates = mapOf(
            requestId1 to null,
            requestId2 to VerifyContractsRequestRedelivery(timestamp, scheduleDelivery, 1, request2),
        )

        val scheduledRunnable = argumentCaptor<Runnable>()
        whenever(scheduledExecutorService.schedule(scheduledRunnable.capture(), any(), any())).thenReturn(mock())

        redeliveryScheduler.onPostCommit(updatedStates)
        scheduledRunnable.firstValue.run()

        verify(scheduledExecutorService).schedule(any(), any(), any())
        val expectedRecord = Record(
            topic = Schemas.Verification.VERIFICATION_LEDGER_PROCESSOR_TOPIC,
            key = requestId2,
            value = request2
        )
        val publishedRecord = argumentCaptor<List<Record<*, *>>>()
        verify(publisher).publish(publishedRecord.capture())
        assertThat(publishedRecord.firstValue).isEqualTo(listOf(expectedRecord))
    }

    @Test
    fun `schedules redeliveries on partition synced`() {
        val requestId = "requestId1"
        val request = createRequest(requestId)
        val timestamp = Instant.now()
        val scheduleDelivery = timestamp.plusMillis(DELAY)
        val states = mapOf(
            requestId to VerifyContractsRequestRedelivery(timestamp, scheduleDelivery, 1, request),
        )

        val scheduledRunnable = argumentCaptor<Runnable>()
        whenever(scheduledExecutorService.schedule(scheduledRunnable.capture(), any(), any())).thenReturn(mock())

        redeliveryScheduler.onPartitionSynced(states)
        scheduledRunnable.firstValue.run()

        verify(scheduledExecutorService).schedule(any(), any(), any())
        val expectedRecord = Record(
            topic = Schemas.Verification.VERIFICATION_LEDGER_PROCESSOR_TOPIC,
            key = requestId,
            value = request
        )
        val publishedRecord = argumentCaptor<List<Record<*, *>>>()
        verify(publisher).publish(publishedRecord.capture())
        assertThat(publishedRecord.firstValue).isEqualTo(listOf(expectedRecord))
    }

    private fun createRequest(requestId: String): VerifyContractsRequest {
        return VerifyContractsRequest().apply {
            timestamp = Instant.MIN
            flowExternalEventContext = ExternalEventContext(requestId, "f1", KeyValuePairList())
            holdingIdentity = mock()
            cpkMetadata = listOf(mock())
        }
    }
}
package net.corda.membership.verification.service.impl

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.p2p.VerificationRequest
import net.corda.data.membership.p2p.VerificationResponse
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.utilities.time.UTCClock
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class MembershipVerificationProcessorTest {
    private companion object {
        const val GROUP_ID = "ABC123"
        const val REQUEST_ID = "REQ-01"

        val clock = UTCClock()
    }

    private val mgm = HoldingIdentity("C=GB, L=London, O=MGM", GROUP_ID).toAvro()
    private val member = HoldingIdentity("C=GB, L=London, O=Alice", GROUP_ID).toAvro()
    private val requestBody = KeyValuePairList(listOf(KeyValuePair("KEY", "dummyKey")))
    private val requestTimestamp = clock.instant()
    private val verificationRequest = VerificationRequest(
        member,
        mgm,
        REQUEST_ID,
        requestTimestamp,
        requestBody
    )

    private val response: KArgumentCaptor<VerificationResponse> = argumentCaptor()
    private val requestSerializer: CordaAvroSerializer<VerificationResponse> = mock {
        on { serialize(response.capture()) } doReturn "RESPONSE".toByteArray()
    }
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory = mock {
        on { createAvroSerializer<VerificationResponse>(any()) } doReturn requestSerializer
    }

    private val membershipVerificationProcessor = MembershipVerificationProcessor(cordaAvroSerializationFactory)

    // TODO write integration test, rebase
    @Test
    fun `processor returns response message`() {
        val result = membershipVerificationProcessor.onNext(events = listOf(Record("topic", "key", verificationRequest)))
        assertThat(result).hasSize(1)
        val appMessage = result.first().value as AppMessage
        with(appMessage.message as AuthenticatedMessage) {
            assertThat(this.header.source).isEqualTo(member)
            assertThat(this.header.destination).isEqualTo(mgm)
            assertThat(this.header.ttl).isEqualTo(1000L)
            assertThat(this.header.messageId).isEqualTo(REQUEST_ID)
            assertThat(this.header.traceId).isEqualTo(REQUEST_ID)
            assertThat(this.header.subsystem).isEqualTo("membership")
        }
        with(response.firstValue) {
            assertThat(this.requestId).isEqualTo(REQUEST_ID)
            assertThat(this.requestTimestamp).isEqualTo(requestTimestamp)
            assertThat(this.responseTimestamp).isAfter(requestTimestamp)
        }
    }
}
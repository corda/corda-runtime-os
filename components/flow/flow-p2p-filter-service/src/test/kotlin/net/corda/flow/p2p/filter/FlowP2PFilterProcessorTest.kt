package net.corda.flow.p2p.filter

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.messaging.api.records.Record
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.schema.Schemas
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant

class FlowP2PFilterProcessorTest {

    private lateinit var processor: FlowP2PFilterProcessor
    private lateinit var factory: CordaAvroSerializationFactory
    private lateinit var deserializer: CordaAvroDeserializer<SessionEvent>

    @BeforeEach
    fun setup() {
        factory = mock()
        deserializer = mock()
        whenever(factory.createAvroDeserializer<SessionEvent>(anyOrNull(), anyOrNull())).thenReturn(deserializer)
    }

    @Test
    fun `validate flow session filter logic transforms sessionId and ignores other subsystems`() {
        val testValue = "test"
        val identity = HoldingIdentity(testValue, testValue)
        val flowHeader =
            AuthenticatedMessageHeader(
                identity, identity, Instant.ofEpochMilli(1), testValue, testValue, "flowSession", MembershipStatusFilter.ACTIVE
            )
        val version = listOf(1)
        val flowEvent = SessionEvent(
            MessageDirection.OUTBOUND,
            Instant.now(),
            testValue,
            1,
            identity,
            identity,
            0,
            listOf(),
            SessionInit(
                testValue,
                version,
                testValue,
                null,
                emptyKeyValuePairList(),
                emptyKeyValuePairList(),
                ByteBuffer.wrap("".toByteArray())
            )
        )

        val flowEventMockData: ByteArray = "flowEvent".toByteArray()
        whenever(deserializer.deserialize(flowEventMockData)).thenReturn(flowEvent)

        val flowRecord = Record(
            Schemas.P2P.P2P_IN_TOPIC,
            testValue,
            AppMessage(AuthenticatedMessage(flowHeader, ByteBuffer.wrap(flowEventMockData)))
        )
        val otherHeader =
            AuthenticatedMessageHeader(
                identity, identity, Instant.ofEpochMilli(1), testValue, testValue, "other", MembershipStatusFilter.ACTIVE
            )
        val otherRecord = Record(
            Schemas.P2P.P2P_IN_TOPIC,
            testValue,
            AppMessage(AuthenticatedMessage(otherHeader, ByteBuffer.wrap("other".toByteArray())))
        )

        val events = listOf(flowRecord, otherRecord)
        processor = FlowP2PFilterProcessor(factory)
        val output = processor.onNext(events)

        assertThat(output.size).isEqualTo(1)
        val first = output.first()
        val flowMapperEvent = first.value as FlowMapperEvent
        val sessionEvent = flowMapperEvent.payload as SessionEvent
        assertThat(first.key).isEqualTo("$testValue-INITIATED")
        assertThat(sessionEvent.sessionId).isEqualTo("$testValue-INITIATED")
        assertThat(sessionEvent.messageDirection).isEqualTo(MessageDirection.INBOUND)
    }
}

package net.corda.flow.p2p.filter

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.identity.HoldingIdentity
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.AuthenticatedMessageHeader
import net.corda.schema.Schemas
import net.corda.v5.base.util.uncheckedCast
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant

class FlowP2PFilterProcessorTest {

    private lateinit var processor: FlowFilterMessageProcessor
    private lateinit var factory: CordaAvroSerializationFactory
    private lateinit var deserializer: CordaAvroDeserializer<FlowMapperEvent>

    @BeforeEach
    fun setup() {
        factory = mock()
        deserializer = mock()
        whenever(factory.createAvroDeserializer<FlowMapperEvent>(anyOrNull(), anyOrNull())).thenReturn(deserializer)
    }
    @Test
    fun `validate flow session filter logic transforms sessionId and ignores other subsystems`() {
        val testValue = "test"
        val identity = HoldingIdentity(testValue, testValue)
        val flowHeader = AuthenticatedMessageHeader(identity, identity, 1, testValue, testValue, "flowSession")
        val flowEvent = FlowMapperEvent(
            SessionEvent(
                MessageDirection.OUTBOUND, Instant.now(), testValue, 1, SessionInit(
                    testValue, testValue, null, identity,
                    identity, ByteBuffer.wrap("".toByteArray())
                )
            )
        )
        val flowEventMockData: ByteArray = "flowEvent".toByteArray()
        whenever(deserializer.deserialize(flowEventMockData)).thenReturn(flowEvent)

        val flowRecord = Record(
            Schemas.P2P.P2P_IN_TOPIC, testValue, AppMessage(AuthenticatedMessage(flowHeader, ByteBuffer.wrap(flowEventMockData)))
        )
        val otherHeader = AuthenticatedMessageHeader(identity, identity, 1, testValue, testValue, "other")
        val otherRecord = Record(
            Schemas.P2P.P2P_IN_TOPIC, testValue, AppMessage(AuthenticatedMessage(otherHeader, ByteBuffer.wrap("other".toByteArray())))
        )

        val events = listOf(flowRecord, otherRecord)
        processor = FlowFilterMessageProcessor(factory)
        val output = processor.onNext(events)

        assertThat(output.size).isEqualTo(1)
        val first = output.first()
        val flowMapperEvent: FlowMapperEvent = uncheckedCast(first.value)
        val sessionEvent: SessionEvent = uncheckedCast(flowMapperEvent.payload)
        assertThat(first.key).isEqualTo("$testValue-INITIATED")
        assertThat(sessionEvent.sessionId).isEqualTo("$testValue-INITIATED")
        assertThat(sessionEvent.messageDirection).isEqualTo(MessageDirection.INBOUND)
    }
}

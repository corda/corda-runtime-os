package net.corda.flow.mapper.impl

import com.typesafe.config.ConfigValueFactory
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.app.AppMessage
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesService
import net.corda.schema.configuration.FlowConfig
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant

internal class RecordFactoryImplTest {

    private lateinit var recordFactoryImplSameCluster: RecordFactoryImpl
    private lateinit var recordFactoryImplDifferentCluster: RecordFactoryImpl

    @BeforeEach
    fun setup() {
        val cordaAvroSerializationFactory: CordaAvroSerializationFactory = mock()
        val cordaAvroSerializer: CordaAvroSerializer<SessionEvent> = mock()
        val byteArray = "SessionEventSerialized".toByteArray()

        whenever(cordaAvroSerializer.serialize(any<SessionEvent>())).thenReturn(byteArray)
        whenever(cordaAvroSerializationFactory.createAvroSerializer<SessionEvent>(anyOrNull())).thenReturn(cordaAvroSerializer)

        val locallyHostedIdentitiesServiceSameCluster: LocallyHostedIdentitiesService = mock()
        whenever(locallyHostedIdentitiesServiceSameCluster.getIdentityInfo(any())).thenReturn(mock())

        val locallyHostedIdentitiesServiceDifferentCluster: LocallyHostedIdentitiesService = mock()
        whenever(locallyHostedIdentitiesServiceDifferentCluster.getIdentityInfo(any())).thenReturn(null)

        recordFactoryImplSameCluster = RecordFactoryImpl(cordaAvroSerializationFactory, locallyHostedIdentitiesServiceSameCluster)
        recordFactoryImplDifferentCluster = RecordFactoryImpl(cordaAvroSerializationFactory, locallyHostedIdentitiesServiceDifferentCluster)
    }
    private val flowConfig = SmartConfigImpl.empty().withValue(FlowConfig.SESSION_P2P_TTL, ConfigValueFactory.fromAnyRef(10000))

    @Test
    fun `forwardError returns record for same cluster`() {
        val sessionEvent = SessionEvent(
            MessageDirection.OUTBOUND,
            Instant.now(), "", 1,
            HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "1"),
            HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "1"),
            SessionError(
                ExceptionEnvelope(
                    "FlowMapper-SessionError",
                    "Received SessionError with sessionId 1"
                )
            ),
            null
        )

        val record = recordFactoryImplSameCluster.forwardError(
            sessionEvent,
            ExceptionEnvelope(
            "FlowMapper-SessionError",
            "Received SessionError with sessionId 1"),
            Instant.now(),
            flowConfig,
            sessionEvent.messageDirection
        )
        Assertions.assertThat(record).isNotNull
        Assertions.assertThat(record.topic).isEqualTo("flow.mapper.event")
        Assertions.assertThat(record.value!!::class).isEqualTo(FlowMapperEvent::class)
    }

    @Test
    fun `forwardEvent returns record for same cluster`() {
        val sessionEvent = SessionEvent(
            MessageDirection.OUTBOUND,
            Instant.now(), "", 1,
            HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "1"),
            HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "1"),
            SessionData(),
            null
        )

        val record = recordFactoryImplSameCluster.forwardEvent(
            sessionEvent,
            Instant.now(),
            flowConfig,
            sessionEvent.messageDirection
        )
        Assertions.assertThat(record).isNotNull
        Assertions.assertThat(record.topic).isEqualTo("flow.mapper.event")
        Assertions.assertThat(record.value!!::class).isEqualTo(FlowMapperEvent::class)
    }

    @Test
    fun `forwardError returns record for different cluster`() {
        val sessionEvent = SessionEvent(
            MessageDirection.OUTBOUND,
            Instant.now(), "", 1,
            HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "1"),
            HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "1"),
            SessionError(
                ExceptionEnvelope(
                    "FlowMapper-SessionError",
                    "Received SessionError with sessionId 1"
                )
            ),
            null
        )

        val record = recordFactoryImplDifferentCluster.forwardError(
            sessionEvent,
            ExceptionEnvelope(
                "FlowMapper-SessionError",
                "Received SessionError with sessionId 1"),
            Instant.now(),
            flowConfig,
            sessionEvent.messageDirection
        )
        Assertions.assertThat(record).isNotNull
        Assertions.assertThat(record.topic).isEqualTo("p2p.out")
        Assertions.assertThat(record.value!!::class).isEqualTo(AppMessage::class)
    }

    @Test
    fun `forwardEvent returns record for different cluster`() {
        val sessionEvent = SessionEvent(
            MessageDirection.OUTBOUND,
            Instant.now(), "", 1,
            HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "1"),
            HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "1"),
            SessionData(
                ByteBuffer.wrap("data".toByteArray()), null)
            ,
            null
        )
        val record = recordFactoryImplDifferentCluster.forwardEvent(
            sessionEvent,
            Instant.now(),
            flowConfig,
            sessionEvent.messageDirection
        )
        Assertions.assertThat(record).isNotNull
        Assertions.assertThat(record.topic).isEqualTo("p2p.out")
        Assertions.assertThat(record.value!!::class).isEqualTo(AppMessage::class)
    }

    @Test
    fun `getSessionEventOutputTopic returns topic when same cluster`() {
        val sessionEvent = SessionEvent(
            MessageDirection.OUTBOUND,
            Instant.now(), "", 1,
            HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "1"),
            HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "1"),
            SessionData(),
            null
        )

        val topic = recordFactoryImplSameCluster.getSessionEventOutputTopic(
            sessionEvent,
            sessionEvent.messageDirection
        )
        Assertions.assertThat(topic).isNotNull
        Assertions.assertThat(topic).isEqualTo("flow.mapper.event")
    }

    @Test
    fun `getSessionEventOutputTopic returns topic when different cluster`() {
        val sessionEvent = SessionEvent(
            MessageDirection.OUTBOUND,
            Instant.now(), "", 1,
            HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "1"),
            HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "1"),
            SessionData(),
            null
        )

        val topic = recordFactoryImplDifferentCluster.getSessionEventOutputTopic(
            sessionEvent,
            sessionEvent.messageDirection
        )
        Assertions.assertThat(topic).isNotNull
        Assertions.assertThat(topic).isEqualTo("p2p.out")
    }
}
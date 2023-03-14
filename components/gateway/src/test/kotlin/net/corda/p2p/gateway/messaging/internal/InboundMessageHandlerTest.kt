package net.corda.p2p.gateway.messaging.internal

import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.gateway.GatewayMessage
import net.corda.data.p2p.gateway.GatewayResponse
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.app.UnauthenticatedMessage
import net.corda.data.p2p.app.UnauthenticatedMessageHeader
import net.corda.data.p2p.crypto.AuthenticatedDataMessage
import net.corda.data.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.data.p2p.crypto.CommonHeader
import net.corda.data.p2p.crypto.InitiatorHandshakeMessage
import net.corda.data.p2p.crypto.InitiatorHelloMessage
import net.corda.data.p2p.crypto.MessageType
import net.corda.data.p2p.crypto.ProtocolMode
import net.corda.data.p2p.crypto.ResponderHandshakeMessage
import net.corda.data.p2p.crypto.ResponderHelloMessage
import net.corda.data.p2p.crypto.internal.InitiatorHandshakeIdentity
import net.corda.p2p.gateway.messaging.http.HttpRequest
import net.corda.p2p.gateway.messaging.http.ReconfigurableHttpServer
import net.corda.p2p.gateway.messaging.session.SessionPartitionMapperImpl
import net.corda.schema.Schemas.P2P.LINK_IN_TOPIC
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.schema.registry.deserialize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.net.InetSocketAddress
import java.nio.ByteBuffer

class InboundMessageHandlerTest {
    private val lifecycleEventHandler = argumentCaptor<LifecycleEventHandler>()
    private val coordinator = mock<LifecycleCoordinator> {
        on { postEvent(any()) } doAnswer {
            lifecycleEventHandler.lastValue.processEvent(it.getArgument(0) as LifecycleEvent, mock)
        }
    }
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), lifecycleEventHandler.capture()) } doReturn coordinator
    }
    private val configurationReaderService = mock<ConfigurationReadService>()
    private val publisherFactory = mock<PublisherFactory> {
        on { createPublisher(any(), any()) } doReturn mock()
    }
    private val subscriptionFactory = mock<SubscriptionFactory>()
    private val server = mockConstruction(ReconfigurableHttpServer::class.java) {mock, _ ->
        val mockDominoTile = mock<ComplexDominoTile> {
            whenever(it.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
        }
        whenever(mock.dominoTile).doReturn(mockDominoTile)
    }
    private val sessionPartitionMapper = mockConstruction(SessionPartitionMapperImpl::class.java) { mock, _ ->
        val mockDominoTile = mock<ComplexDominoTile> {
            whenever(it.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
        }
        whenever(mock.dominoTile).doReturn(mockDominoTile)
    }
    private val p2pInPublisher = mockConstruction(PublisherWithDominoLogic::class.java) { mock, _ ->
        val mockDominoTile = mock<ComplexDominoTile> {
            whenever(it.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
        }
        whenever(mock.dominoTile).doReturn(mockDominoTile)
    }

    private val dominoTile = mockConstruction(ComplexDominoTile::class.java) { mock, _ ->
        @Suppress("UNCHECKED_CAST")
        whenever(mock.withLifecycleLock(any<() -> Any>())).doAnswer { (it.arguments.first() as () -> Any).invoke() }
    }

    private val requestId = "id"
    private val serialisedMessage = "gateway-message".toByteArray()
    private val serialisedResponse = "gateway-response".toByteArray()
    private val avroSchemaRegistry = mock<AvroSchemaRegistry> {
        on { serialize(any<GatewayResponse>()) } doReturn ByteBuffer.wrap(serialisedResponse)
        on { deserialize<GatewayMessage>(ByteBuffer.wrap(serialisedMessage)) } doReturn
                GatewayMessage(requestId, authenticatedP2PDataMessage(""))
    }
    private val handler = InboundMessageHandler(
        lifecycleCoordinatorFactory,
        configurationReaderService,
        publisherFactory,
        subscriptionFactory,
        SmartConfigImpl.empty(),
        mock(),
        avroSchemaRegistry
    )

    @AfterEach
    fun cleanUp() {
        server.close()
        sessionPartitionMapper.close()
        p2pInPublisher.close()
        dominoTile.close()
    }

    @Test
    fun `onMessage will respond with error if handler is not running`() {
        val sessionId = "aaa"
        whenever(sessionPartitionMapper.constructed().first().getPartitions(sessionId)).doReturn(listOf(1, 2, 3))
        handler.onRequest(
            HttpRequest(
                source = InetSocketAddress("www.r3.com", 1231),
                payload = serialisedMessage,
                destination = InetSocketAddress("www.r3.com", 344),
            )

        )

        verify(server.constructed().first())
            .writeResponse(
                HttpResponseStatus.SERVICE_UNAVAILABLE,
                InetSocketAddress("www.r3.com", 1231)
            )
    }

    @Test
    fun `onMessage will respond with error if message content is wrong`() {
        setRunning()
        val invalidMessage = "invalid-message".toByteArray()
        `when`(avroSchemaRegistry.deserialize<GatewayMessage>(ByteBuffer.wrap(invalidMessage))).thenThrow(RuntimeException())
        handler.onRequest(
            HttpRequest(
                source = InetSocketAddress("www.r3.com", 1231),
                payload = invalidMessage,
                destination = InetSocketAddress("www.r3.com", 344),
            )
        )

        verify(server.constructed().first())
            .writeResponse(
                HttpResponseStatus.BAD_REQUEST,
                InetSocketAddress("www.r3.com", 1231)
            )
    }

    @Test
    fun `onMessage will respond with OK with valid message`() {
        setRunning()
        val sessionId = "aaa"
        whenever(sessionPartitionMapper.constructed().first().getPartitions(sessionId)).doReturn(listOf(1, 2, 3))
        val p2pMessage = authenticatedP2PDataMessage(sessionId)
        val gatewayMessage = GatewayMessage(requestId, p2pMessage)
        `when`(avroSchemaRegistry.deserialize<GatewayMessage>(ByteBuffer.wrap(serialisedMessage))).thenReturn(gatewayMessage)
        handler.onRequest(
            HttpRequest(
                source = InetSocketAddress("www.r3.com", 1231),
                payload = serialisedMessage,
                destination = InetSocketAddress("www.r3.com", 344),
            )

        )

        verify(server.constructed().first())
            .writeResponse(
                HttpResponseStatus.OK,
                InetSocketAddress("www.r3.com", 1231),
                serialisedResponse
            )
    }

    @Test
    fun `onMessage will respond with OK with valid unauthenticated message`() {
        setRunning()
        val msgId = "msg-id"
        val p2pMessage = unauthenticatedP2PMessage("abc")
        val gatewayMessage = GatewayMessage(msgId, p2pMessage)
        `when`(avroSchemaRegistry.deserialize<GatewayMessage>(ByteBuffer.wrap(serialisedMessage))).thenReturn(gatewayMessage)
        handler.onRequest(
            HttpRequest(
                source = InetSocketAddress("www.r3.com", 1231),
                payload = serialisedMessage,
                destination = InetSocketAddress("www.r3.com", 344),
            )

        )

        verify(server.constructed().first())
            .writeResponse(
                HttpResponseStatus.OK,
                InetSocketAddress("www.r3.com", 1231),
                serialisedResponse
            )
    }

    @Test
    fun `onMessage unauthenticated message will publish a message`() {
        val published = argumentCaptor<List<Record<*, *>>>()
        whenever(p2pInPublisher.constructed().first().publish(published.capture())).doReturn(mock())
        setRunning()

        val p2pMessage = unauthenticatedP2PMessage("abc")
        val gatewayMessage = GatewayMessage("msg-id", p2pMessage)
        val linkInMessage = LinkInMessage(p2pMessage)
        `when`(avroSchemaRegistry.deserialize<GatewayMessage>(ByteBuffer.wrap(serialisedMessage))).thenReturn(gatewayMessage)
        handler.onRequest(
            HttpRequest(
                source = InetSocketAddress("www.r3.com", 1231),
                payload = serialisedMessage,
                destination = InetSocketAddress("www.r3.com", 344),
            )

        )

        assertThat(published.firstValue).hasSize(1)
            .anyMatch {
                (it.topic == LINK_IN_TOPIC) &&
                    (it.value == linkInMessage)
            }
    }

    @Test
    fun `onMessage authenticated message will publish a message to the correct partition`() {
        val published = argumentCaptor<List<Pair<Int, Record<*, *>>>>()
        val sessionId = "aaa"
        whenever(sessionPartitionMapper.constructed().first().getPartitions(sessionId)).doReturn(listOf(7, 10, 20))
        whenever(p2pInPublisher.constructed().first().publishToPartition(published.capture())).doReturn(mock())
        setRunning()
        val p2pMessage = authenticatedP2PDataMessage(sessionId)
        val gatewayMessage = GatewayMessage("msg-id", p2pMessage)
        val linkInMessage = LinkInMessage(p2pMessage)
        `when`(avroSchemaRegistry.deserialize<GatewayMessage>(ByteBuffer.wrap(serialisedMessage))).thenReturn(gatewayMessage)
        handler.onRequest(
            HttpRequest(
                source = InetSocketAddress("www.r3.com", 1231),
                payload = serialisedMessage,
                destination = InetSocketAddress("www.r3.com", 344),
            )

        )

        assertThat(published.firstValue).hasSize(1)
            .anyMatch { (partition, record) ->
                (record.topic == LINK_IN_TOPIC) &&
                    (record.value == linkInMessage) &&
                    ((partition == 7) || (partition == 10) || (partition == 20))
            }
    }

    @Test
    fun `onMessage session initiator hello message is published when no session exists yet`() {
        val published = argumentCaptor<List<Record<*, *>>>()
        val sessionId = "aaa"
        whenever(p2pInPublisher.constructed().first().publish(published.capture())).doReturn(mock())
        setRunning()
        val p2pMessage = authenticatedP2PInitiatorHelloMessage(sessionId)
        val gatewayMessage = GatewayMessage("msg-id", p2pMessage)
        val linkInMessage = LinkInMessage(p2pMessage)
        `when`(avroSchemaRegistry.deserialize<GatewayMessage>(ByteBuffer.wrap(serialisedMessage))).thenReturn(gatewayMessage)
        handler.onRequest(
            HttpRequest(
                source = InetSocketAddress("www.r3.com", 1231),
                payload = serialisedMessage,
                destination = InetSocketAddress("www.r3.com", 344),
            )

        )

        assertThat(published.firstValue).hasSize(1)
            .anyMatch { record ->
                (record.topic == LINK_IN_TOPIC) && (record.value == linkInMessage) && (record.key == sessionId)
            }
    }

    @Test
    fun `onMessage authenticated message with no partition will reply with an error`() {
        whenever(sessionPartitionMapper.constructed().first().getPartitions(any())).doReturn(null)
        setRunning()
        val msgId = "msg-id"
        val gatewayMessage = GatewayMessage(msgId, authenticatedP2PDataMessage(""))
        `when`(avroSchemaRegistry.deserialize<GatewayMessage>(ByteBuffer.wrap(serialisedMessage))).thenReturn(gatewayMessage)
        handler.onRequest(
            HttpRequest(
                source = InetSocketAddress("www.r3.com", 1231),
                payload = serialisedMessage,
                destination = InetSocketAddress("www.r3.com", 344),
            )

        )

        verify(server.constructed().first())
            .writeResponse(
                HttpResponseStatus.GONE,
                InetSocketAddress("www.r3.com", 1231),
                serialisedResponse
            )
    }

    @Test
    fun `onMessage authenticated message will respond with error for invalid session ID`() {
        setRunning()
        val msgId = "msg-id"
        mockStatic(GatewayMessage::class.java).use {
            val header = mock<CommonHeader> {
                on { sessionId } doReturn null
            }
            val payload = mock<AuthenticatedDataMessage> {
                on { getHeader() } doReturn header
            }
            val gatewayMessage = mock<GatewayMessage> {
                on { getPayload() } doReturn payload
                on { id } doReturn msgId
            }
            `when`(avroSchemaRegistry.deserialize<GatewayMessage>(ByteBuffer.wrap(serialisedMessage))).thenReturn(gatewayMessage)

            handler.onRequest(
                HttpRequest(
                    source = InetSocketAddress("www.r3.com", 1231),
                    payload = serialisedMessage,
                    destination = InetSocketAddress("www.r3.com", 344),
                )
            )

            verify(server.constructed().first())
                .writeResponse(
                    HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    InetSocketAddress("www.r3.com", 1231),
                    serialisedResponse
                )
        }
    }

    @Test
    fun `onMessage use the correct session ID from AuthenticatedDataMessage payload`() {
        val sessionId = "id"
        setRunning()
        val payload = AuthenticatedDataMessage.newBuilder()
            .apply {
                header = CommonHeader(MessageType.DATA, 0, sessionId, 1, 1)
                payload = ByteBuffer.wrap(byteArrayOf())
                authTag = ByteBuffer.wrap(byteArrayOf())
            }.build()
        val gatewayMessage = GatewayMessage("msg-id", payload)
        `when`(avroSchemaRegistry.deserialize<GatewayMessage>(ByteBuffer.wrap(serialisedMessage))).thenReturn(gatewayMessage)
        whenever(sessionPartitionMapper.constructed().first().getPartitions(any())).doReturn(null)

        handler.onRequest(
            HttpRequest(
                source = InetSocketAddress("www.r3.com", 1231),
                payload = serialisedMessage,
                destination = InetSocketAddress("www.r3.com", 344),
            )
        )

        verify(sessionPartitionMapper.constructed().first()).getPartitions(sessionId)
    }

    @Test
    fun `onMessage use the correct session ID from AuthenticatedEncryptedDataMessage payload`() {
        val sessionId = "id"
        setRunning()
        val payload = AuthenticatedEncryptedDataMessage.newBuilder()
            .apply {
                header = CommonHeader(MessageType.DATA, 0, sessionId, 1, 1)
                encryptedPayload = ByteBuffer.wrap(byteArrayOf())
                authTag = ByteBuffer.wrap(byteArrayOf())
            }.build()
        val gatewayMessage = GatewayMessage("msg-id", payload)
        `when`(avroSchemaRegistry.deserialize<GatewayMessage>(ByteBuffer.wrap(serialisedMessage))).thenReturn(gatewayMessage)
        whenever(sessionPartitionMapper.constructed().first().getPartitions(any())).doReturn(null)

        handler.onRequest(
            HttpRequest(
                source = InetSocketAddress("www.r3.com", 1231),
                payload = serialisedMessage,
                destination = InetSocketAddress("www.r3.com", 344),
            )
        )

        verify(sessionPartitionMapper.constructed().first()).getPartitions(sessionId)
    }

    @Test
    fun `onMessage use the correct session ID from InitiatorHandshakeMessage payload`() {
        val sessionId = "id"
        setRunning()
        val payload = InitiatorHandshakeMessage.newBuilder()
            .apply {
                header = CommonHeader(MessageType.DATA, 0, sessionId, 1, 1)
                encryptedData = ByteBuffer.wrap(byteArrayOf())
                authTag = ByteBuffer.wrap(byteArrayOf())
            }.build()
        val gatewayMessage = GatewayMessage("msg-id", payload)
        `when`(avroSchemaRegistry.deserialize<GatewayMessage>(ByteBuffer.wrap(serialisedMessage))).thenReturn(gatewayMessage)
        whenever(sessionPartitionMapper.constructed().first().getPartitions(any())).doReturn(null)

        handler.onRequest(
            HttpRequest(
                source = InetSocketAddress("www.r3.com", 1231),
                payload = serialisedMessage,
                destination = InetSocketAddress("www.r3.com", 344),
            )
        )

        verify(sessionPartitionMapper.constructed().first()).getPartitions(sessionId)
    }

    @Test
    fun `onMessage use the correct session ID from ResponderHelloMessage payload`() {
        val sessionId = "id"
        setRunning()
        val payload = ResponderHelloMessage.newBuilder()
            .apply {
                header = CommonHeader(MessageType.DATA, 0, sessionId, 1, 1)
                responderPublicKey = ByteBuffer.wrap(byteArrayOf())
                selectedMode = ProtocolMode.AUTHENTICATION_ONLY
            }.build()
        val gatewayMessage = GatewayMessage("msg-id", payload)
        `when`(avroSchemaRegistry.deserialize<GatewayMessage>(ByteBuffer.wrap(serialisedMessage))).thenReturn(gatewayMessage)
        whenever(sessionPartitionMapper.constructed().first().getPartitions(any())).doReturn(null)

        handler.onRequest(
            HttpRequest(
                source = InetSocketAddress("www.r3.com", 1231),
                payload = serialisedMessage,
                destination = InetSocketAddress("www.r3.com", 344),
            )
        )

        verify(sessionPartitionMapper.constructed().first()).getPartitions(sessionId)
    }

    @Test
    fun `onMessage use the correct session ID from ResponderHandshakeMessage payload`() {
        val sessionId = "id"
        setRunning()
        val payload = ResponderHandshakeMessage.newBuilder()
            .apply {
                header = CommonHeader(MessageType.DATA, 0, sessionId, 1, 1)
                encryptedData = ByteBuffer.wrap(byteArrayOf())
                authTag = ByteBuffer.wrap(byteArrayOf())
            }.build()
        val gatewayMessage = GatewayMessage("msg-id", payload)
        `when`(avroSchemaRegistry.deserialize<GatewayMessage>(ByteBuffer.wrap(serialisedMessage))).thenReturn(gatewayMessage)
        whenever(sessionPartitionMapper.constructed().first().getPartitions(any())).doReturn(null)

        handler.onRequest(
            HttpRequest(
                source = InetSocketAddress("www.r3.com", 1231),
                payload = serialisedMessage,
                destination = InetSocketAddress("www.r3.com", 344),
            )
        )

        verify(sessionPartitionMapper.constructed().first()).getPartitions(sessionId)
    }

    private fun setRunning() {
        whenever(dominoTile.constructed().first().isRunning).doReturn(true)
        whenever(server.constructed().first().isRunning).doReturn(true)
        whenever(sessionPartitionMapper.constructed().first().isRunning).doReturn(true)
        whenever(p2pInPublisher.constructed().first().isRunning).doReturn(true)
        handler.start()
    }

    private fun authenticatedP2PDataMessage(sessionId: String) = AuthenticatedDataMessage.newBuilder().apply {
        header = CommonHeader(MessageType.DATA, 0, sessionId, 1, 1)
        payload = ByteBuffer.wrap(byteArrayOf())
        authTag = ByteBuffer.wrap(byteArrayOf())
    }.build()

    private fun authenticatedP2PInitiatorHelloMessage(sessionId: String) = InitiatorHelloMessage.newBuilder().apply {
        header = CommonHeader(MessageType.INITIATOR_HELLO, 0, sessionId, 1, 1)
        initiatorPublicKey = ByteBuffer.wrap(byteArrayOf())
        supportedModes = listOf(ProtocolMode.AUTHENTICATED_ENCRYPTION, ProtocolMode.AUTHENTICATION_ONLY)
        source = InitiatorHandshakeIdentity(ByteBuffer.wrap(byteArrayOf()), "some-group")
    }.build()

    private fun unauthenticatedP2PMessage(content: String) = UnauthenticatedMessage.newBuilder().apply {
        header = UnauthenticatedMessageHeader(
            HoldingIdentity("A", "B"),
            HoldingIdentity("C", "D"),
            "subsystem",
            "messageId",
        )
        payload = ByteBuffer.wrap(content.toByteArray())
    }.build()

    @Test
    fun `onMessage authenticated message with empty partition will reply with an error`() {
        whenever(sessionPartitionMapper.constructed().first().getPartitions(any())).doReturn(emptyList())
        setRunning()
        handler.onRequest(
            HttpRequest(
                source = InetSocketAddress("www.r3.com", 1231),
                payload = serialisedMessage,
                destination = InetSocketAddress("www.r3.com", 344),
            )

        )

        verify(server.constructed().first())
            .writeResponse(
                HttpResponseStatus.GONE,
                InetSocketAddress("www.r3.com", 1231),
                serialisedResponse
            )
    }
}

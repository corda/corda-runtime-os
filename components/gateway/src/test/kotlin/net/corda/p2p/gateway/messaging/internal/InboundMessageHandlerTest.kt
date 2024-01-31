package net.corda.p2p.gateway.messaging.internal

import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.configuration.read.ConfigurationReadService
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
import net.corda.data.p2p.app.InboundUnauthenticatedMessage
import net.corda.data.p2p.app.InboundUnauthenticatedMessageHeader
import net.corda.data.p2p.crypto.AuthenticatedDataMessage
import net.corda.data.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.data.p2p.crypto.CommonHeader
import net.corda.data.p2p.crypto.InitiatorHandshakeMessage
import net.corda.data.p2p.crypto.InitiatorHelloMessage
import net.corda.data.p2p.crypto.MessageType
import net.corda.data.p2p.crypto.ResponderHandshakeMessage
import net.corda.data.p2p.crypto.ResponderHelloMessage
import net.corda.data.p2p.crypto.internal.InitiatorHandshakeIdentity
import net.corda.data.p2p.linkmanager.LinkManagerResponse
import net.corda.messaging.api.publisher.HttpRpcClient
import net.corda.p2p.gateway.messaging.http.HttpRequest
import net.corda.p2p.gateway.messaging.http.HttpWriter
import net.corda.p2p.gateway.messaging.http.ReconfigurableHttpServer
import net.corda.p2p.gateway.messaging.session.SessionPartitionMapperImpl
import net.corda.schema.Schemas.P2P.LINK_IN_TOPIC
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.schema.registry.deserialize
import net.corda.utilities.flags.Features
import net.corda.v5.base.exceptions.CordaRuntimeException
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
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
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
    private val httpRpcClient = mock<HttpRpcClient>()
    private val publisherFactory = mock<PublisherFactory> {
        on { createPublisher(any(), any()) } doReturn mock()
        on { createHttpRpcClient() } doReturn httpRpcClient
    }
    private val subscriptionFactory = mock<SubscriptionFactory>()
    private val server = mockConstruction(ReconfigurableHttpServer::class.java) { mock, _ ->
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
    private val writer = mock<HttpWriter>()

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
    private val features = mock<Features> {
        on { enableP2PGatewayToLinkManagerOverHttp } doReturn false
        on { useStatefulSessionManager } doReturn false
    }
    private val commonComponents = mock<CommonComponents> {
        on { features } doReturn features
    }
    private val handler = InboundMessageHandler(
        lifecycleCoordinatorFactory,
        configurationReaderService,
        publisherFactory,
        subscriptionFactory,
        SmartConfigImpl.empty(),
        commonComponents,
        avroSchemaRegistry,
        mock(),
        mock(),
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
            writer,
            HttpRequest(
                source = InetSocketAddress("www.r3.com", 1231),
                payload = serialisedMessage,
                destination = InetSocketAddress("www.r3.com", 344),
            )

        )

        verify(writer)
            .write(
                HttpResponseStatus.SERVICE_UNAVAILABLE,
                InetSocketAddress("www.r3.com", 1231),
            )
    }

    @Test
    fun `onMessage will respond with error if message content is wrong`() {
        setRunning()
        val invalidMessage = "invalid-message".toByteArray()
        `when`(avroSchemaRegistry.deserialize<GatewayMessage>(ByteBuffer.wrap(invalidMessage))).thenThrow(RuntimeException())
        handler.onRequest(
            writer,
            HttpRequest(
                source = InetSocketAddress("www.r3.com", 1231),
                payload = invalidMessage,
                destination = InetSocketAddress("www.r3.com", 344),
            )
        )

        verify(writer)
            .write(
                HttpResponseStatus.BAD_REQUEST,
                InetSocketAddress("www.r3.com", 1231),
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
            writer,
            HttpRequest(
                source = InetSocketAddress("www.r3.com", 1231),
                payload = serialisedMessage,
                destination = InetSocketAddress("www.r3.com", 344),
            )

        )

        verify(writer)
            .write(
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
            writer,
            HttpRequest(
                source = InetSocketAddress("www.r3.com", 1231),
                payload = serialisedMessage,
                destination = InetSocketAddress("www.r3.com", 344),
            )

        )

        verify(writer)
            .write(
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
            writer,
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
            writer,
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
            writer,
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
            writer,
            HttpRequest(
                source = InetSocketAddress("www.r3.com", 1231),
                payload = serialisedMessage,
                destination = InetSocketAddress("www.r3.com", 344),
            )

        )

        verify(writer)
            .write(
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
                writer,
                HttpRequest(
                    source = InetSocketAddress("www.r3.com", 1231),
                    payload = serialisedMessage,
                    destination = InetSocketAddress("www.r3.com", 344),
                )
            )

            verify(writer)
                .write(
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
            writer,
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
            writer,
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
            writer,
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
            }.build()
        val gatewayMessage = GatewayMessage("msg-id", payload)
        `when`(avroSchemaRegistry.deserialize<GatewayMessage>(ByteBuffer.wrap(serialisedMessage))).thenReturn(gatewayMessage)
        whenever(sessionPartitionMapper.constructed().first().getPartitions(any())).doReturn(null)

        handler.onRequest(
            writer,
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
            writer,
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
        source = InitiatorHandshakeIdentity(ByteBuffer.wrap(byteArrayOf()), "some-group")
    }.build()

    private fun unauthenticatedP2PMessage(content: String) = InboundUnauthenticatedMessage.newBuilder().apply {
        header = InboundUnauthenticatedMessageHeader(
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
            writer,
            HttpRequest(
                source = InetSocketAddress("www.r3.com", 1231),
                payload = serialisedMessage,
                destination = InetSocketAddress("www.r3.com", 344),
            )

        )

        verify(writer)
            .write(
                HttpResponseStatus.GONE,
                InetSocketAddress("www.r3.com", 1231),
                serialisedResponse
            )
    }

    @Test
    fun `when enableP2PGatewayToLinkManagerOverHttp is false, avoid sending message to HTTP client`() {
        setRunning()
        val msgId = "msg-id"
        val p2pMessage = unauthenticatedP2PMessage("abc")
        val gatewayMessage = GatewayMessage(msgId, p2pMessage)
        whenever(avroSchemaRegistry.deserialize<GatewayMessage>(ByteBuffer.wrap(serialisedMessage))).thenReturn(gatewayMessage)

        handler.onRequest(
            writer,
            HttpRequest(
                source = InetSocketAddress("www.r3.com", 1231),
                payload = serialisedMessage,
                destination = InetSocketAddress("www.r3.com", 344),
            )

        )

        verify(httpRpcClient, never()).send(any(), any(), eq(GatewayResponse::class.java))
    }

    @Test
    fun `when enableP2PGatewayToLinkManagerOverHttp is true, avoid publishing data to the bus`() {
        whenever(features.enableP2PGatewayToLinkManagerOverHttp).doReturn(true)
        setRunning()
        val msgId = "msg-id"
        val p2pMessage = unauthenticatedP2PMessage("abc")
        val gatewayMessage = GatewayMessage(msgId, p2pMessage)
        whenever(avroSchemaRegistry.deserialize<GatewayMessage>(ByteBuffer.wrap(serialisedMessage))).thenReturn(gatewayMessage)

        handler.onRequest(
            writer,
            HttpRequest(
                source = InetSocketAddress("www.r3.com", 1231),
                payload = serialisedMessage,
                destination = InetSocketAddress("www.r3.com", 344),
            )

        )

        verify(p2pInPublisher.constructed().first(), never()).publish(any())
    }

    @Test
    fun `when sending messages to the link manager succeed, OK is written to the writer`() {
        whenever(features.enableP2PGatewayToLinkManagerOverHttp).doReturn(true)
        setRunning()
        val msgId = "msg-id"
        val p2pMessage = unauthenticatedP2PMessage("abc")
        val gatewayMessage = GatewayMessage(msgId, p2pMessage)
        whenever(avroSchemaRegistry.deserialize<GatewayMessage>(ByteBuffer.wrap(serialisedMessage))).thenReturn(gatewayMessage)

        handler.onRequest(
            writer,
            HttpRequest(
                source = InetSocketAddress("www.r3.com", 1231),
                payload = serialisedMessage,
                destination = InetSocketAddress("www.r3.com", 344),
            )

        )

        verify(writer).write(eq(HttpResponseStatus.OK), any(), any())
    }

    @Test
    fun `when sending messages to the link manager failed, INTERNAL_SERVER_ERROR is written to the writer`() {
        whenever(features.enableP2PGatewayToLinkManagerOverHttp).doReturn(true)
        setRunning()
        val msgId = "msg-id"
        val p2pMessage = unauthenticatedP2PMessage("abc")
        val gatewayMessage = GatewayMessage(msgId, p2pMessage)
        whenever(avroSchemaRegistry.deserialize<GatewayMessage>(ByteBuffer.wrap(serialisedMessage))).thenReturn(gatewayMessage)
        whenever(httpRpcClient.send(any(), any(), eq(LinkManagerResponse::class.java))).doThrow(CordaRuntimeException("Oops"))

        handler.onRequest(
            writer,
            HttpRequest(
                source = InetSocketAddress("www.r3.com", 1231),
                payload = serialisedMessage,
                destination = InetSocketAddress("www.r3.com", 344),
            )

        )

        verify(writer).write(eq(HttpResponseStatus.INTERNAL_SERVER_ERROR), any(), any())
    }

    @Test
    fun `link manager payload is forwarded to the writer`() {
        whenever(features.enableP2PGatewayToLinkManagerOverHttp).doReturn(true)
        setRunning()
        val msgId = "msg-id"
        val p2pMessage = unauthenticatedP2PMessage("abc")
        val message = argumentCaptor<GatewayResponse>()
        whenever(avroSchemaRegistry.serialize(message.capture())).doReturn(ByteBuffer.wrap(serialisedResponse))
        val payload = AuthenticatedEncryptedDataMessage.newBuilder()
            .apply {
                header = CommonHeader(MessageType.DATA, 0, "sessionId", 1, 1)
                encryptedPayload = ByteBuffer.wrap(byteArrayOf())
                authTag = ByteBuffer.wrap(byteArrayOf())
            }.build()
        val gatewayMessage = GatewayMessage(msgId, p2pMessage)
        whenever(avroSchemaRegistry.deserialize<GatewayMessage>(ByteBuffer.wrap(serialisedMessage))).thenReturn(gatewayMessage)
        whenever(
            httpRpcClient.send(
                any(),
                eq(LinkInMessage(p2pMessage)),
                eq(LinkManagerResponse::class.java),
            )
        ).doReturn(
            LinkManagerResponse(
                payload,
            )
        )

        handler.onRequest(
            writer,
            HttpRequest(
                source = InetSocketAddress("www.r3.com", 1231),
                payload = serialisedMessage,
                destination = InetSocketAddress("www.r3.com", 344),
            )

        )

        assertThat(message.firstValue).isEqualTo(
            GatewayResponse(
                msgId,
                payload,
            )
        )
    }

    @Test
    fun `link manager response without payload is replied without payload`() {
        whenever(features.enableP2PGatewayToLinkManagerOverHttp).doReturn(true)
        setRunning()
        val msgId = "msg-id"
        val p2pMessage = unauthenticatedP2PMessage("abc")
        val message = argumentCaptor<GatewayResponse>()
        whenever(avroSchemaRegistry.serialize(message.capture())).doReturn(ByteBuffer.wrap(serialisedResponse))
        val gatewayMessage = GatewayMessage(msgId, p2pMessage)
        whenever(avroSchemaRegistry.deserialize<GatewayMessage>(ByteBuffer.wrap(serialisedMessage))).thenReturn(gatewayMessage)
        whenever(
            httpRpcClient.send(
                any(),
                eq(LinkInMessage(p2pMessage)),
                eq(LinkManagerResponse::class.java),
            )
        ).doReturn(
            LinkManagerResponse(
                null,
            )
        )

        handler.onRequest(
            writer,
            HttpRequest(
                source = InetSocketAddress("www.r3.com", 1231),
                payload = serialisedMessage,
                destination = InetSocketAddress("www.r3.com", 344),
            )

        )

        assertThat(message.firstValue).isEqualTo(
            GatewayResponse(
                msgId,
                null,
            )
        )
    }

    @Test
    fun `when useStatefulSessionManager is true, custom partitioning for inbound session messages is turned off`() {
        whenever(features.useStatefulSessionManager).doReturn(true)
        whenever(sessionPartitionMapper.constructed().first().getPartitions(any())).doReturn(mock())
        setRunning()
        val msgId = "msg-id"
        val gatewayMessage = GatewayMessage(msgId, authenticatedP2PDataMessage(""))
        whenever(avroSchemaRegistry.deserialize<GatewayMessage>(ByteBuffer.wrap(serialisedMessage))).thenReturn(gatewayMessage)

        handler.onRequest(
            writer,
            HttpRequest(
                source = InetSocketAddress("www.r3.com", 1231),
                payload = serialisedMessage,
                destination = InetSocketAddress("www.r3.com", 344),
            )

        )

        verify(p2pInPublisher.constructed().first())
            .publish(any())
        verify(sessionPartitionMapper.constructed().first(), never())
            .getPartitions(any())
    }
}

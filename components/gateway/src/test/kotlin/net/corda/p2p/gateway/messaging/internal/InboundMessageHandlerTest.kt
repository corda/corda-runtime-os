package net.corda.p2p.gateway.messaging.internal

import com.typesafe.config.ConfigFactory
import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.LinkInMessage
import net.corda.p2p.app.HoldingIdentity
import net.corda.p2p.app.UnauthenticatedMessage
import net.corda.p2p.app.UnauthenticatedMessageHeader
import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.p2p.crypto.CommonHeader
import net.corda.p2p.crypto.InitiatorHandshakeMessage
import net.corda.p2p.crypto.InitiatorHelloMessage
import net.corda.p2p.crypto.MessageType
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.ResponderHandshakeMessage
import net.corda.p2p.crypto.ResponderHelloMessage
import net.corda.p2p.crypto.internal.InitiatorHandshakeIdentity
import net.corda.p2p.gateway.messaging.http.HttpMessage
import net.corda.p2p.gateway.messaging.http.ReconfigurableHttpServer
import net.corda.p2p.gateway.messaging.session.SessionPartitionMapperImpl
import net.corda.p2p.schema.Schema.Companion.LINK_IN_TOPIC
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
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
    private val server = mockConstruction(ReconfigurableHttpServer::class.java)
    private val sessionPartitionMapper = mockConstruction(SessionPartitionMapperImpl::class.java)
    private val p2pInPublisher = mockConstruction(PublisherWithDominoLogic::class.java)

    private val handler = InboundMessageHandler(
        lifecycleCoordinatorFactory,
        configurationReaderService,
        publisherFactory,
        subscriptionFactory,
        ConfigFactory.empty(),
    )

    @AfterEach
    fun cleanUp() {
        server.close()
        sessionPartitionMapper.close()
        p2pInPublisher.close()
    }

    @Test
    fun `children return the correct children`() {
        assertThat(handler.children).containsExactlyInAnyOrder(
            p2pInPublisher.constructed().first(),
            sessionPartitionMapper.constructed().first(),
            server.constructed().first()
        )
    }

    @Test
    fun `onMessage will respond with error if handler is not running`() {
        val sessionId = "aaa"
        whenever(sessionPartitionMapper.constructed().first().getPartitions(sessionId)).doReturn(listOf(1, 2, 3))
        handler.onMessage(
            HttpMessage(
                source = InetSocketAddress("www.r3.com", 1231),
                statusCode = HttpResponseStatus.OK,
                payload = LinkInMessage(authenticatedP2PMessage(sessionId)).toByteBuffer().array(),
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
    fun `onMessage will respond with error if message had error`() {
        setRunning()
        handler.onMessage(
            HttpMessage(
                source = InetSocketAddress("www.r3.com", 1231),
                statusCode = HttpResponseStatus.BAD_GATEWAY,
                payload = byteArrayOf(1),
                destination = InetSocketAddress("www.r3.com", 344),
            )
        )

        verify(server.constructed().first())
            .writeResponse(
                HttpResponseStatus.BAD_GATEWAY,
                InetSocketAddress("www.r3.com", 1231)
            )
    }

    @Test
    fun `onMessage will respond with error if message content is wrong`() {
        setRunning()
        handler.onMessage(
            HttpMessage(
                source = InetSocketAddress("www.r3.com", 1231),
                statusCode = HttpResponseStatus.OK,
                payload = byteArrayOf(1, 2, 4),
                destination = InetSocketAddress("www.r3.com", 344),
            )
        )

        verify(server.constructed().first())
            .writeResponse(
                HttpResponseStatus.INTERNAL_SERVER_ERROR,
                InetSocketAddress("www.r3.com", 1231)
            )
    }

    @Test
    fun `onMessage will respond with OK with valid message`() {
        setRunning()
        val sessionId = "aaa"
        whenever(sessionPartitionMapper.constructed().first().getPartitions(sessionId)).doReturn(listOf(1, 2, 3))
        handler.onMessage(
            HttpMessage(
                source = InetSocketAddress("www.r3.com", 1231),
                statusCode = HttpResponseStatus.OK,
                payload = LinkInMessage(authenticatedP2PMessage(sessionId)).toByteBuffer().array(),
                destination = InetSocketAddress("www.r3.com", 344),
            )

        )

        verify(server.constructed().first())
            .writeResponse(
                HttpResponseStatus.OK,
                InetSocketAddress("www.r3.com", 1231)
            )
    }

    @Test
    fun `onMessage will respond with OK with valid unauthenticated message`() {
        setRunning()
        handler.onMessage(
            HttpMessage(
                source = InetSocketAddress("www.r3.com", 1231),
                statusCode = HttpResponseStatus.OK,
                payload = LinkInMessage(unauthenticatedP2PMessage("")).toByteBuffer().array(),
                destination = InetSocketAddress("www.r3.com", 344),
            )

        )

        verify(server.constructed().first())
            .writeResponse(
                HttpResponseStatus.OK,
                InetSocketAddress("www.r3.com", 1231)
            )
    }

    @Test
    fun `onMessage unauthenticated message will publish a message`() {
        val published = argumentCaptor<List<Record<*, *>>>()
        whenever(p2pInPublisher.constructed().first().publish(published.capture())).doReturn(mock())
        setRunning()
        val p2pMessage = LinkInMessage(unauthenticatedP2PMessage("abc"))
        handler.onMessage(
            HttpMessage(
                source = InetSocketAddress("www.r3.com", 1231),
                statusCode = HttpResponseStatus.OK,
                payload = p2pMessage.toByteBuffer().array(),
                destination = InetSocketAddress("www.r3.com", 344),
            )

        )

        assertThat(published.firstValue).hasSize(1)
            .anyMatch {
                (it.topic == LINK_IN_TOPIC) &&
                    (it.value == p2pMessage)
            }
    }

    @Test
    fun `onMessage authenticated message will publish a message to the correct partition`() {
        val published = argumentCaptor<List<Pair<Int, Record<*, *>>>>()
        val sessionId = "aaa"
        whenever(sessionPartitionMapper.constructed().first().getPartitions(sessionId)).doReturn(listOf(7, 10, 20))
        whenever(p2pInPublisher.constructed().first().publishToPartition(published.capture())).doReturn(mock())
        setRunning()
        val p2pMessage = LinkInMessage(authenticatedP2PMessage(sessionId))
        handler.onMessage(
            HttpMessage(
                source = InetSocketAddress("www.r3.com", 1231),
                statusCode = HttpResponseStatus.OK,
                payload = p2pMessage.toByteBuffer().array(),
                destination = InetSocketAddress("www.r3.com", 344),
            )

        )

        assertThat(published.firstValue).hasSize(1)
            .anyMatch { (partition, record) ->
                (record.topic == LINK_IN_TOPIC) &&
                    (record.value == p2pMessage) &&
                    ((partition == 7) || (partition == 10) || (partition == 20))
            }
    }

    @Test
    fun `onMessage authenticated message with no partition will reply with an error`() {
        whenever(sessionPartitionMapper.constructed().first().getPartitions(any())).doReturn(null)
        setRunning()
        val p2pMessage = LinkInMessage(authenticatedP2PMessage(""))
        handler.onMessage(
            HttpMessage(
                source = InetSocketAddress("www.r3.com", 1231),
                statusCode = HttpResponseStatus.OK,
                payload = p2pMessage.toByteBuffer().array(),
                destination = InetSocketAddress("www.r3.com", 344),
            )

        )

        verify(server.constructed().first())
            .writeResponse(
                HttpResponseStatus.INTERNAL_SERVER_ERROR,
                InetSocketAddress("www.r3.com", 1231)
            )
    }

    @Test
    fun `onMessage authenticated message will respond with error for invalid session ID`() {
        setRunning()
        mockStatic(LinkInMessage::class.java).use {
            val header = mock<CommonHeader> {
                on { sessionId } doReturn null
            }
            val payload = mock<AuthenticatedDataMessage> {
                on { getHeader() } doReturn header
            }
            val message = mock<LinkInMessage> {
                on { getPayload() } doReturn payload
                on { schema } doReturn mock()
            }
            it.`when`<LinkInMessage> {
                LinkInMessage.fromByteBuffer(any())
            }.doReturn(message)

            handler.onMessage(
                HttpMessage(
                    source = InetSocketAddress("www.r3.com", 1231),
                    statusCode = HttpResponseStatus.OK,
                    payload = byteArrayOf(),
                    destination = InetSocketAddress("www.r3.com", 344),
                )
            )

            verify(server.constructed().first())
                .writeResponse(
                    HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    InetSocketAddress("www.r3.com", 1231)
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
        val p2pMessage = LinkInMessage(payload)
        whenever(sessionPartitionMapper.constructed().first().getPartitions(any())).doReturn(null)

        handler.onMessage(
            HttpMessage(
                source = InetSocketAddress("www.r3.com", 1231),
                statusCode = HttpResponseStatus.OK,
                payload = p2pMessage.toByteBuffer().array(),
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
        val p2pMessage = LinkInMessage(payload)
        whenever(sessionPartitionMapper.constructed().first().getPartitions(any())).doReturn(null)

        handler.onMessage(
            HttpMessage(
                source = InetSocketAddress("www.r3.com", 1231),
                statusCode = HttpResponseStatus.OK,
                payload = p2pMessage.toByteBuffer().array(),
                destination = InetSocketAddress("www.r3.com", 344),
            )
        )

        verify(sessionPartitionMapper.constructed().first()).getPartitions(sessionId)
    }

    @Test
    fun `onMessage use the correct session ID from InitiatorHelloMessage payload`() {
        val sessionId = "id"
        setRunning()
        val payload = InitiatorHelloMessage.newBuilder()
            .apply {
                header = CommonHeader(MessageType.DATA, 0, sessionId, 1, 1)
                initiatorPublicKey = ByteBuffer.wrap(byteArrayOf())
                supportedModes = emptyList()
                source = InitiatorHandshakeIdentity(ByteBuffer.wrap(byteArrayOf()), "")
            }.build()
        val p2pMessage = LinkInMessage(payload)
        whenever(sessionPartitionMapper.constructed().first().getPartitions(any())).doReturn(null)

        handler.onMessage(
            HttpMessage(
                source = InetSocketAddress("www.r3.com", 1231),
                statusCode = HttpResponseStatus.OK,
                payload = p2pMessage.toByteBuffer().array(),
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
        val p2pMessage = LinkInMessage(payload)
        whenever(sessionPartitionMapper.constructed().first().getPartitions(any())).doReturn(null)

        handler.onMessage(
            HttpMessage(
                source = InetSocketAddress("www.r3.com", 1231),
                statusCode = HttpResponseStatus.OK,
                payload = p2pMessage.toByteBuffer().array(),
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
        val p2pMessage = LinkInMessage(payload)
        whenever(sessionPartitionMapper.constructed().first().getPartitions(any())).doReturn(null)

        handler.onMessage(
            HttpMessage(
                source = InetSocketAddress("www.r3.com", 1231),
                statusCode = HttpResponseStatus.OK,
                payload = p2pMessage.toByteBuffer().array(),
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
        val p2pMessage = LinkInMessage(payload)
        whenever(sessionPartitionMapper.constructed().first().getPartitions(any())).doReturn(null)

        handler.onMessage(
            HttpMessage(
                source = InetSocketAddress("www.r3.com", 1231),
                statusCode = HttpResponseStatus.OK,
                payload = p2pMessage.toByteBuffer().array(),
                destination = InetSocketAddress("www.r3.com", 344),
            )
        )

        verify(sessionPartitionMapper.constructed().first()).getPartitions(sessionId)
    }

    private fun setRunning() {
        whenever(server.constructed().first().isRunning).doReturn(true)
        whenever(sessionPartitionMapper.constructed().first().isRunning).doReturn(true)
        whenever(p2pInPublisher.constructed().first().isRunning).doReturn(true)
        handler.start()
    }

    private fun authenticatedP2PMessage(sessionId: String) = AuthenticatedDataMessage.newBuilder().apply {
        header = CommonHeader(MessageType.DATA, 0, sessionId, 1, 1)
        payload = ByteBuffer.wrap(byteArrayOf())
        authTag = ByteBuffer.wrap(byteArrayOf())
    }.build()
    private fun unauthenticatedP2PMessage(content: String) = UnauthenticatedMessage.newBuilder().apply {
        header = UnauthenticatedMessageHeader(
            HoldingIdentity("A", "B"),
            HoldingIdentity("C", "D")
        )
        payload = ByteBuffer.wrap(content.toByteArray())
    }.build()
}

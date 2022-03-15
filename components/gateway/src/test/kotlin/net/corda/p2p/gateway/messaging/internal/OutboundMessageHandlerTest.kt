package net.corda.p2p.gateway.messaging.internal

import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.gateway.GatewayMessage
import net.corda.data.p2p.gateway.GatewayResponse
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.LinkOutHeader
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.NetworkType
import net.corda.p2p.app.UnauthenticatedMessage
import net.corda.p2p.app.UnauthenticatedMessageHeader
import net.corda.p2p.gateway.messaging.ConnectionConfiguration
import net.corda.p2p.gateway.messaging.ReconfigurableConnectionManager
import net.corda.p2p.gateway.messaging.http.DestinationInfo
import net.corda.p2p.gateway.messaging.http.HttpClient
import net.corda.p2p.gateway.messaging.http.HttpResponse
import net.corda.p2p.gateway.messaging.http.TrustStoresMap
import net.corda.test.util.MockTimeFacilitiesProvider
import net.corda.v5.base.util.millis
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.x500.X500Name
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.net.URI
import java.nio.ByteBuffer
import java.security.KeyStore
import java.util.concurrent.CompletableFuture

class OutboundMessageHandlerTest {
    companion object {
        private const val GROUP_ID = "My group ID"
    }

    private val mockTimeFacilitiesProvider = MockTimeFacilitiesProvider()

    private val coordinatorHandler = argumentCaptor<LifecycleEventHandler>()
    private val coordinator = mock<LifecycleCoordinator> {
        on { postEvent(any()) } doAnswer {
            coordinatorHandler.lastValue.processEvent(it.getArgument(0) as LifecycleEvent, mock)
        }
    }
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), coordinatorHandler.capture()) } doReturn coordinator
    }

    private val configurationReaderService = mock<ConfigurationReadService>()
    private val subscription = mock<Subscription<String, LinkOutMessage>>()
    private val subscriptionFactory = mock<SubscriptionFactory> {
        on {
            createEventLogSubscription(
                any(),
                any<EventLogProcessor<String, LinkOutMessage>>(),
                any(),
                anyOrNull()
            )
        } doReturn subscription
    }
    private var connectionConfig = ConnectionConfiguration()
    private val connectionManager = mockConstruction(ReconfigurableConnectionManager::class.java)
    private val truststore = mock<KeyStore>()
    private val trustStores = mockConstruction(TrustStoresMap::class.java) { mock, _ ->
        whenever(mock.getTrustStore(GROUP_ID)).doReturn(truststore)
    }

    private val sentMessages = mutableListOf<GatewayMessage>()
    private val client = mock<HttpClient> {
        on { write(any()) } doAnswer {
            val gatewayMessage = GatewayMessage.fromByteBuffer(ByteBuffer.wrap(it.arguments[0] as ByteArray))
            sentMessages.add(gatewayMessage)
            val httpResponse = mock<HttpResponse> {
                on { statusCode } doReturn HttpResponseStatus.OK
                on { payload } doReturn GatewayResponse(gatewayMessage.id).toByteBuffer().array()
            }
            CompletableFuture.completedFuture(httpResponse)
        }
    }

    private var handlerStarted = true
    private val dominoTile = mockConstruction(ComplexDominoTile::class.java) { mock, _ ->
        @Suppress("UNCHECKED_CAST")
        whenever(mock.withLifecycleLock(any<() -> Any>())).doAnswer { (it.arguments.first() as () -> Any).invoke() }
        whenever(mock.isRunning).doAnswer { handlerStarted }
    }
    private val subscriptionTile = mockConstruction(SubscriptionDominoTile::class.java)
    private val connectionConfigReader = mockConstruction(ConnectionConfigReader::class.java) { mock, _ ->
        whenever(mock.connectionConfig) doAnswer { connectionConfig }
    }

    private val handler = OutboundMessageHandler(
        lifecycleCoordinatorFactory,
        configurationReaderService,
        subscriptionFactory,
        SmartConfigImpl.empty(),
        2,
        mockTimeFacilitiesProvider.mockScheduledExecutor
    )

    @AfterEach
    fun cleanUp() {
        trustStores.close()
        connectionManager.close()
        dominoTile.close()
        subscriptionTile.close()
        connectionConfigReader.close()
    }

    @Test
    fun `onNext will write message to the client and return empty list`() {
        val msgPayload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D")
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader(
            HoldingIdentity("a", GROUP_ID),
            NetworkType.CORDA_5,
            "https://r3.com/",
        )
        val message = LinkOutMessage(headers, msgPayload)
        whenever(connectionManager.constructed().first().acquire(any())).doReturn(client)

        val events = handler.onNext(
            listOf(
                EventLogRecord("", "", message, 1, 1L),
                EventLogRecord("", "", null, 2, 2L)
            )
        )

        assertThat(sentMessages).hasSize(1)
        assertThat(sentMessages.first().payload).isEqualTo(msgPayload)
        assertThat(events).isEmpty()
    }

    @Test
    fun `onNext will throw an exception if the handler is not ready`() {
        handlerStarted = false
        val payload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D")
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader(
            HoldingIdentity("a", GROUP_ID),
            NetworkType.CORDA_5,
            "https://r3.com/",
        )
        val message = LinkOutMessage(headers, payload)
        whenever(connectionManager.constructed().first().acquire(any())).doReturn(client)

        assertThrows<IllegalStateException> {
            handler.onNext(
                listOf(
                    EventLogRecord("", "", message, 1, 1L),
                    EventLogRecord("", "", null, 2, 2L)
                )
            )
        }
    }

    @Test
    fun `onNext will use the correct destination info for CORDA5`() {
        val payload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D")
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader(
            HoldingIdentity("a", GROUP_ID),
            NetworkType.CORDA_5,
            "https://r3.com/",
        )
        val message = LinkOutMessage(
            headers,
            payload,
        )
        val destinationInfo = argumentCaptor<DestinationInfo>()
        whenever(connectionManager.constructed().first().acquire(destinationInfo.capture())).doReturn(client)

        handler.onNext(
            listOf(
                EventLogRecord("", "", message, 1, 1L),
            )
        )

        assertThat(destinationInfo.firstValue)
            .isEqualTo(
                DestinationInfo(
                    URI.create("https://r3.com/"),
                    "r3.com",
                    null,
                    truststore
                )
            )
    }

    @Test
    fun `onNext will use the correct destination info for CORDA4`() {
        val payload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D")
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader(
            HoldingIdentity("O=PartyA, L=London, C=GB", GROUP_ID),
            NetworkType.CORDA_4,
            "https://r3.com/",
        )
        val message = LinkOutMessage(
            headers,
            payload,
        )
        val destinationInfo = argumentCaptor<DestinationInfo>()
        whenever(connectionManager.constructed().first().acquire(destinationInfo.capture())).doReturn(client)

        handler.onNext(
            listOf(
                EventLogRecord("", "", message, 1, 1L),
            )
        )

        assertThat(destinationInfo.firstValue)
            .isEqualTo(
                DestinationInfo(
                    URI.create("https://r3.com/"),
                    "b597e8858a2fa87424f5e8c39dc4f93c.p2p.corda.net",
                    X500Name("O=PartyA, L=London, C=GB"),
                    truststore
                )
            )
    }

    @Test
    fun `onNext will not send anything for invalid arguments`() {
        val payload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D")
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader(
            HoldingIdentity("aaa", GROUP_ID),
            NetworkType.CORDA_4,
            "https://r3.com/",
        )
        val message = LinkOutMessage(
            headers,
            payload,
        )

        handler.onNext(
            listOf(
                EventLogRecord("", "", message, 1, 1L),
            )
        )

        verify(connectionManager.constructed().first(), never()).acquire(any())
    }

    @Test
    fun `onNext will get the trust store from the trust store map`() {
        val payload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D")
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader(
            HoldingIdentity("aaa", GROUP_ID),
            NetworkType.CORDA_4,
            "https://r3.com/",
        )

        val message = LinkOutMessage(
            headers,
            payload,
        )

        handler.onNext(
            listOf(
                EventLogRecord("", "", message, 1, 1L),
            )
        )

        verify(trustStores.constructed().first()).getTrustStore(GROUP_ID)
    }

    @Test
    fun `when message times out, it is retried once`() {
        connectionConfig = ConnectionConfiguration().copy(responseTimeout = 10.millis, retryDelay = 10.millis)
        val client = mock<HttpClient> {
            on { write(any()) } doAnswer {
                val gatewayMessage = GatewayMessage.fromByteBuffer(ByteBuffer.wrap(it.arguments[0] as ByteArray))
                sentMessages.add(gatewayMessage)
                CompletableFuture<HttpResponse>()
                // simulate scenario where no response is received.
            }
        }
        val msgPayload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D")
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader(
            HoldingIdentity("a", GROUP_ID),
            NetworkType.CORDA_5,
            "https://r3.com/",
        )
        val message = LinkOutMessage(headers, msgPayload)
        whenever(connectionManager.constructed().first().acquire(any())).doReturn(client)

        handler.onNext(
            listOf(
                EventLogRecord("", "", message, 1, 1L),
                EventLogRecord("", "", null, 2, 2L)
            )
        )

        mockTimeFacilitiesProvider.advanceTime(connectionConfig.retryDelay)
        assertThat(sentMessages).hasSize(2)
        sentMessages.forEach {
            assertThat(it.payload).isEqualTo(msgPayload)
        }

        repeat(2) { mockTimeFacilitiesProvider.advanceTime(connectionConfig.retryDelay.multipliedBy(2)) }
        assertThat(sentMessages).hasSize(2)
    }

    @Test
    fun `when message fails, it is retried once`() {
        connectionConfig = ConnectionConfiguration().copy(responseTimeout = 10.millis, retryDelay = 10.millis)
        val client = mock<HttpClient> {
            on { write(any()) } doAnswer {
                val gatewayMessage = GatewayMessage.fromByteBuffer(ByteBuffer.wrap(it.arguments[0] as ByteArray))
                sentMessages.add(gatewayMessage)
                CompletableFuture.failedFuture(RuntimeException("some error happened"))
            }
        }
        val msgPayload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D")
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader(
            HoldingIdentity("a", GROUP_ID),
            NetworkType.CORDA_5,
            "https://r3.com/",
        )
        val message = LinkOutMessage(headers, msgPayload)
        whenever(connectionManager.constructed().first().acquire(any())).doReturn(client)

        handler.onNext(
            listOf(
                EventLogRecord("", "", message, 1, 1L),
                EventLogRecord("", "", null, 2, 2L)
            )
        )

        mockTimeFacilitiesProvider.advanceTime(connectionConfig.retryDelay)
        assertThat(sentMessages).hasSize(2)
        sentMessages.forEach {
            assertThat(it.payload).isEqualTo(msgPayload)
        }

        repeat(2) { mockTimeFacilitiesProvider.advanceTime(connectionConfig.retryDelay.multipliedBy(2)) }
        assertThat(sentMessages).hasSize(2)
    }

    @Test
    fun `when 5xx error code is received, it is retried once`() {
        connectionConfig = ConnectionConfiguration().copy(retryDelay = 10.millis)
        val client = mock<HttpClient> {
            on { write(any()) } doAnswer {
                val gatewayMessage = GatewayMessage.fromByteBuffer(ByteBuffer.wrap(it.arguments[0] as ByteArray))
                sentMessages.add(gatewayMessage)
                val response = mock<HttpResponse> {
                    on { statusCode } doReturn HttpResponseStatus.INTERNAL_SERVER_ERROR
                }
                CompletableFuture.completedFuture(response)
            }
        }
        val msgPayload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D")
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader(
            HoldingIdentity("a", GROUP_ID),
            NetworkType.CORDA_5,
            "https://r3.com/",
        )
        val message = LinkOutMessage(headers, msgPayload)
        whenever(connectionManager.constructed().first().acquire(any())).doReturn(client)

        handler.onNext(
            listOf(
                EventLogRecord("", "", message, 1, 1L),
                EventLogRecord("", "", null, 2, 2L)
            )
        )

        mockTimeFacilitiesProvider.advanceTime(connectionConfig.retryDelay)
        assertThat(sentMessages).hasSize(2)
        sentMessages.forEach {
            assertThat(it.payload).isEqualTo(msgPayload)
        }

        repeat(2) { mockTimeFacilitiesProvider.advanceTime(connectionConfig.retryDelay.multipliedBy(2)) }
        assertThat(sentMessages).hasSize(2)
    }

    @Test
    fun `when 4xx error code is received, it is not retried`() {
        val retryDelay = 10.millis
        connectionConfig = ConnectionConfiguration().copy(responseTimeout = 10.millis, retryDelay = retryDelay)
        val client = mock<HttpClient> {
            on { write(any()) } doAnswer {
                val gatewayMessage = GatewayMessage.fromByteBuffer(ByteBuffer.wrap(it.arguments[0] as ByteArray))
                sentMessages.add(gatewayMessage)
                val response = mock<HttpResponse> {
                    on { statusCode } doReturn HttpResponseStatus.BAD_REQUEST
                }
                CompletableFuture.completedFuture(response)
            }
        }
        val msgPayload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D")
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader(
            HoldingIdentity("a", GROUP_ID),
            NetworkType.CORDA_5,
            "https://r3.com/",
        )
        val message = LinkOutMessage(headers, msgPayload)
        whenever(connectionManager.constructed().first().acquire(any())).doReturn(client)

        handler.onNext(
            listOf(
                EventLogRecord("", "", message, 1, 1L),
                EventLogRecord("", "", null, 2, 2L)
            )
        )

        repeat(2) { mockTimeFacilitiesProvider.advanceTime(connectionConfig.retryDelay.multipliedBy(2)) }
        assertThat(sentMessages).hasSize(1)
        assertThat(sentMessages.first().payload).isEqualTo(msgPayload)
    }
}

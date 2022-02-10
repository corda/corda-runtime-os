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
import net.corda.lifecycle.domino.logic.ConfigurationChangeHandler
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
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
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import net.corda.p2p.gateway.messaging.ReconfigurableConnectionManager
import net.corda.p2p.gateway.messaging.http.DestinationInfo
import net.corda.p2p.gateway.messaging.http.HttpClient
import net.corda.p2p.gateway.messaging.http.HttpResponse
import net.corda.v5.base.util.millis
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.x500.X500Name
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OutboundMessageHandlerTest {
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
    private val gatewayConfig = mock<GatewayConfiguration> {
        on { connectionConfig } doAnswer { connectionConfig }
    }
    private val connectionManager = mockConstruction(ReconfigurableConnectionManager::class.java)

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

    private lateinit var createResources: ((resources: ResourcesHolder) -> CompletableFuture<Unit>)
    private lateinit var configHandler: ConfigurationChangeHandler<GatewayConfiguration>
    private val dominoTile = mockConstruction(DominoTile::class.java) { mock, context ->
        @Suppress("UNCHECKED_CAST")
        whenever(mock.withLifecycleLock(any<() -> Any>())).doAnswer { (it.arguments.first() as () -> Any).invoke() }
        @Suppress("UNCHECKED_CAST")
        createResources = context.arguments()[2] as ((resources: ResourcesHolder) -> CompletableFuture<Unit>)
        @Suppress("UNCHECKED_CAST")
        configHandler = (context.arguments()[5] as ConfigurationChangeHandler<GatewayConfiguration>)
    }

    private val handler = OutboundMessageHandler(
        lifecycleCoordinatorFactory,
        configurationReaderService,
        subscriptionFactory,
        SmartConfigImpl.empty(),
        2,
    )

    @AfterEach
    fun cleanUp() {
        connectionManager.close()
        dominoTile.close()
    }

    @Test
    fun `createResources will start a subscription`() {
        startHandler()

        val resourcesHolder = mock<ResourcesHolder>()
        createResources(resourcesHolder)

        verify(subscription).start()
    }

    @Test
    fun `createResources keeps the subscription in the resource holder`() {
        startHandler()

        val resourcesHolder = mock<ResourcesHolder>()
        createResources(resourcesHolder)
        //TODOs : this will be refactored as part of CORE-3147
        //verify(resourcesHolder).keep(subscription)
    }

    @Test
    fun `createResources completes the future`() {
        startHandler()

        val resourcesHolder = mock<ResourcesHolder>()
        val future = createResources(resourcesHolder)
        assertThat(future.isDone).isTrue
        assertThat(future.isCompletedExceptionally).isFalse
    }

    @Test
    fun `onNext will throw an exception if the handler is not ready`() {
        val payload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D")
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader("a", NetworkType.CORDA_5, "https://r3.com/")
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
    fun `onNext will write message to the client and return empty list`() {
        startHandler()
        val msgPayload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D")
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader("a", NetworkType.CORDA_5, "https://r3.com/")
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
    fun `onNext will use the correct destination info for CORDA5`() {
        startHandler()
        val payload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D")
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader("a", NetworkType.CORDA_5, "https://r3.com/")
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
                    null
                )
            )
    }

    @Test
    fun `onNext will use the correct destination info for CORDA4`() {
        startHandler()
        val payload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D")
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader("O=PartyA, L=London, C=GB", NetworkType.CORDA_4, "https://r3.com/")
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
                    X500Name("O=PartyA, L=London, C=GB")
                )
            )
    }

    @Test
    fun `onNext will not send anything for invalid arguments`() {
        startHandler()
        val payload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D")
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader("aaa", NetworkType.CORDA_4, "https://r3.com/")
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
    fun `when message times out, it is retried once`() {
        connectionConfig = ConnectionConfiguration().copy(responseTimeout = 10.millis, retryDelay = 10.millis)
        startHandler()
        val messagesLatch = CountDownLatch(2)
        val client = mock<HttpClient> {
            on { write(any()) } doAnswer {
                val gatewayMessage = GatewayMessage.fromByteBuffer(ByteBuffer.wrap(it.arguments[0] as ByteArray))
                sentMessages.add(gatewayMessage)
                messagesLatch.countDown()
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
        val headers = LinkOutHeader("a", NetworkType.CORDA_5, "https://r3.com/")
        val message = LinkOutMessage(headers, msgPayload)
        whenever(connectionManager.constructed().first().acquire(any())).doReturn(client)

        handler.onNext(
            listOf(
                EventLogRecord("", "", message, 1, 1L),
                EventLogRecord("", "", null, 2, 2L)
            )
        )

        assertTrue(messagesLatch.await(1, TimeUnit.SECONDS)) { "Not enough attempts to send message." }
        assertThat(sentMessages).hasSize(2)
        sentMessages.forEach {
            assertThat(it.payload).isEqualTo(msgPayload)
        }

        val waitTime = ((connectionConfig.responseTimeout.toMillis() + connectionConfig.retryDelay.toMillis()) * 4).toInt().millis
        Thread.sleep(waitTime.toMillis())
        assertThat(sentMessages).hasSize(2)
    }

    @Test
    fun `when message fails, it is retried once`() {
        connectionConfig = ConnectionConfiguration().copy(responseTimeout = 10.millis, retryDelay = 10.millis)
        startHandler()
        val messagesLatch = CountDownLatch(2)
        val client = mock<HttpClient> {
            on { write(any()) } doAnswer {
                val gatewayMessage = GatewayMessage.fromByteBuffer(ByteBuffer.wrap(it.arguments[0] as ByteArray))
                sentMessages.add(gatewayMessage)
                messagesLatch.countDown()
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
        val headers = LinkOutHeader("a", NetworkType.CORDA_5, "https://r3.com/")
        val message = LinkOutMessage(headers, msgPayload)
        whenever(connectionManager.constructed().first().acquire(any())).doReturn(client)

        handler.onNext(
            listOf(
                EventLogRecord("", "", message, 1, 1L),
                EventLogRecord("", "", null, 2, 2L)
            )
        )

        assertTrue(messagesLatch.await(1, TimeUnit.SECONDS)) { "Not enough attempts to send message." }
        assertThat(sentMessages).hasSize(2)
        sentMessages.forEach {
            assertThat(it.payload).isEqualTo(msgPayload)
        }

        val waitTime = ((connectionConfig.responseTimeout.toMillis() + connectionConfig.retryDelay.toMillis()) * 4).toInt().millis
        Thread.sleep(waitTime.toMillis())
        assertThat(sentMessages).hasSize(2)
    }

    @Test
    fun `when 5xx error code is received, it is retried once`() {
        connectionConfig = ConnectionConfiguration().copy(retryDelay = 10.millis)
        startHandler()
        val messagesLatch = CountDownLatch(2)
        val client = mock<HttpClient> {
            on { write(any()) } doAnswer {
                val gatewayMessage = GatewayMessage.fromByteBuffer(ByteBuffer.wrap(it.arguments[0] as ByteArray))
                sentMessages.add(gatewayMessage)
                messagesLatch.countDown()
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
        val headers = LinkOutHeader("a", NetworkType.CORDA_5, "https://r3.com/")
        val message = LinkOutMessage(headers, msgPayload)
        whenever(connectionManager.constructed().first().acquire(any())).doReturn(client)

        handler.onNext(
            listOf(
                EventLogRecord("", "", message, 1, 1L),
                EventLogRecord("", "", null, 2, 2L)
            )
        )

        assertTrue(messagesLatch.await(1, TimeUnit.SECONDS)) { "Not enough attempts to send message." }
        assertThat(sentMessages).hasSize(2)
        sentMessages.forEach {
            assertThat(it.payload).isEqualTo(msgPayload)
        }

        val waitTime = ((connectionConfig.responseTimeout.toMillis() + connectionConfig.retryDelay.toMillis()) * 4).toInt().millis
        Thread.sleep(waitTime.toMillis())
        assertThat(sentMessages).hasSize(2)
    }

    @Test
    fun `when 4xx error code is received, it is not retried`() {
        val retryDelay = 10.millis
        connectionConfig = ConnectionConfiguration().copy(responseTimeout = 10.millis, retryDelay = retryDelay)
        startHandler()
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
        val headers = LinkOutHeader("a", NetworkType.CORDA_5, "https://r3.com/")
        val message = LinkOutMessage(headers, msgPayload)
        whenever(connectionManager.constructed().first().acquire(any())).doReturn(client)

        handler.onNext(
            listOf(
                EventLogRecord("", "", message, 1, 1L),
                EventLogRecord("", "", null, 2, 2L)
            )
        )

        val waitTime = ((connectionConfig.responseTimeout.toMillis() + connectionConfig.retryDelay.toMillis()) * 4).toInt().millis
        Thread.sleep(waitTime.toMillis())
        assertThat(sentMessages).hasSize(1)
        assertThat(sentMessages.first().payload).isEqualTo(msgPayload)
    }

    private fun startHandler() {
        whenever(connectionManager.constructed().first().isRunning).doReturn(true)
        whenever(dominoTile.constructed().first().isRunning).doReturn(true)
        handler.start()
        configHandler.applyNewConfiguration(gatewayConfig, null, ResourcesHolder())
    }
}

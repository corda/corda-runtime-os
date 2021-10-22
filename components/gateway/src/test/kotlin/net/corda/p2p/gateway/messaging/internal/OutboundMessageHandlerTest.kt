package net.corda.p2p.gateway.messaging.internal

import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutHeader
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.NetworkType
import net.corda.p2p.app.HoldingIdentity
import net.corda.p2p.app.UnauthenticatedMessage
import net.corda.p2p.app.UnauthenticatedMessageHeader
import net.corda.p2p.gateway.messaging.ReconfigurableConnectionManager
import net.corda.p2p.gateway.messaging.http.DestinationInfo
import net.corda.p2p.gateway.messaging.http.HttpClient
import net.corda.p2p.gateway.messaging.http.HttpMessage
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.x500.X500Name
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
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
import java.net.InetSocketAddress
import java.net.URI
import java.nio.ByteBuffer

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
    private val connectionManager = mockConstruction(ReconfigurableConnectionManager::class.java)

    private val handler = OutboundMessageHandler(
        lifecycleCoordinatorFactory,
        configurationReaderService,
        subscriptionFactory,
    )

    @AfterEach
    fun cleanUp() {
        connectionManager.close()
    }

//    @Test
//    fun `children return all the children`() {
//        assertThat(handler.children).containsExactlyInAnyOrder(
//            connectionManager.constructed().first(),
//        )
//    }

    @Test
    fun `start will start a subscription`() {
        whenever(connectionManager.constructed().first().isRunning).doReturn(true)

        handler.start()

        verify(subscription).start()
    }

    @Test
    fun `stop will stop the subscription`() {
        startHandler()

        handler.stop()

        verify(subscription).stop()
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
        val message = LinkOutMessage(
            headers,
            payload,
        )
        val client = mock<HttpClient>()
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
    fun `onNext will write message to the client`() {
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
        val client = mock<HttpClient>()
        whenever(connectionManager.constructed().first().acquire(any())).doReturn(client)

        handler.onNext(
            listOf(
                EventLogRecord("", "", message, 1, 1L),
                EventLogRecord("", "", null, 2, 2L)
            )
        )

        verify(client).write(LinkInMessage(payload).toByteBuffer().array())
    }

    @Test
    fun `onNext will return empty list`() {
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
        val client = mock<HttpClient>()
        whenever(connectionManager.constructed().first().acquire(any())).doReturn(client)

        val events = handler.onNext(
            listOf(
                EventLogRecord("", "", message, 1, 1L),
                EventLogRecord("", "", null, 2, 2L)
            )
        )

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
        val client = mock<HttpClient>()
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
        val client = mock<HttpClient>()
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
    fun `onMessage will handle OK message without exception`() {
        val message = HttpMessage(
            statusCode = HttpResponseStatus.OK,
            payload = ByteArray(0),
            source = InetSocketAddress("www.r3.com", 30),
            destination = InetSocketAddress("www.r3.com", 31),
        )
        assertDoesNotThrow {
            handler.onMessage(message)
        }
    }

    @Test
    fun `onMessage will handle Error message without exception`() {
        val message = HttpMessage(
            statusCode = HttpResponseStatus.BAD_REQUEST,
            payload = ByteArray(0),
            source = InetSocketAddress("www.r3.com", 30),
            destination = InetSocketAddress("www.r3.com", 31),
        )
        assertDoesNotThrow {
            handler.onMessage(message)
        }
    }

    private fun startHandler() {
        whenever(connectionManager.constructed().first().isRunning).doReturn(true)
        handler.start()
    }
}

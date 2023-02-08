package net.corda.p2p.gateway.messaging.internal

import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.gateway.GatewayMessage
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.data.p2p.LinkOutHeader
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.NetworkType
import net.corda.data.p2p.app.UnauthenticatedMessage
import net.corda.data.p2p.app.UnauthenticatedMessageHeader
import net.corda.p2p.gateway.messaging.ConnectionConfiguration
import net.corda.p2p.gateway.messaging.DynamicKeyStore
import net.corda.p2p.gateway.messaging.ReconfigurableConnectionManager
import net.corda.p2p.gateway.messaging.SslConfiguration
import net.corda.p2p.gateway.messaging.TlsType
import net.corda.p2p.gateway.messaging.http.DestinationInfo
import net.corda.p2p.gateway.messaging.http.HttpClient
import net.corda.p2p.gateway.messaging.http.HttpResponse
import net.corda.p2p.gateway.messaging.http.KeyStoreWithPassword
import net.corda.p2p.gateway.messaging.http.TrustStoresMap
import net.corda.test.util.time.MockTimeFacilitiesProvider
import net.corda.v5.base.types.MemberX500Name
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.net.URI
import java.nio.ByteBuffer
import java.security.KeyStore
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.ScheduledExecutorService

class OutboundMessageHandlerTest {
    companion object {
        private const val GROUP_ID = "My group ID"
        private const val VALID_X500_NAME = "CN=Alice, O=Alice Corp, L=LDN, C=GB"
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
    private val connectionManager = mockConstruction(ReconfigurableConnectionManager::class.java) { mock, _ ->
        val mockDominoTile = mock<ComplexDominoTile> {
            whenever(it.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
        }
        whenever(mock.dominoTile).doReturn(mockDominoTile)
    }
    private val truststore = mock<KeyStore>()
    private val trustStoresMap = mock<TrustStoresMap> {
        on { getTrustStore(any(), eq(GROUP_ID)) } doReturn truststore
    }

    private val sentMessages = mutableListOf<ByteArray>()
    private val client = mock<HttpClient> {
        on { write(any()) } doAnswer {
            sentMessages.add(it.arguments[0] as ByteArray)
            val httpResponse = mock<HttpResponse> {
                on { statusCode } doReturn HttpResponseStatus.OK
            }
            CompletableFuture.completedFuture(httpResponse)
        }
    }

    private var handlerStarted = true
    private var onClose: (() -> Unit)? = null
    private val dominoTile = mockConstruction(ComplexDominoTile::class.java) { mock, context ->
        @Suppress("UNCHECKED_CAST")
        whenever(mock.withLifecycleLock(any<() -> Any>())).doAnswer { (it.arguments.first() as () -> Any).invoke() }
        whenever(mock.isRunning).doAnswer { handlerStarted }
        @Suppress("UNCHECKED_CAST")
        onClose = context.arguments()[3] as? (() -> Unit)
    }
    private val subscriptionTile = mockConstruction(SubscriptionDominoTile::class.java) { mock, _ ->
        whenever(mock.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
    }
    private val gatewayConfigReader = mockConstruction(GatewayConfigReader::class.java) { mock, _ ->
        whenever(mock.connectionConfig) doAnswer { connectionConfig }
        val mockDominoTile = mock<ComplexDominoTile> {
            whenever(it.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
        }
        whenever(mock.dominoTile).doReturn(mockDominoTile)
    }
    private val commonComponentsDominoTile = mock<ComplexDominoTile> {
        whenever(mock.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
    }
    private val commonComponents = mock<CommonComponents> {
        on { dominoTile } doReturn commonComponentsDominoTile
        on { trustStoresMap } doReturn trustStoresMap
    }

    private val serialisedMessage = "gateway-message".toByteArray()
    private val handler = OutboundMessageHandler(
        lifecycleCoordinatorFactory,
        configurationReaderService,
        subscriptionFactory,
        SmartConfigImpl.empty(),
        mock {
            on { serialize(any<GatewayMessage>()) } doReturn ByteBuffer.wrap(serialisedMessage)
        },
        commonComponents,
        ) { mockTimeFacilitiesProvider.mockScheduledExecutor }

    @AfterEach
    fun cleanUp() {
        connectionManager.close()
        dominoTile.close()
        subscriptionTile.close()
        gatewayConfigReader.close()
    }

    @Test
    fun `onClose closes the scheduled executor service`() {
        val mockExecutorService = mock<ScheduledExecutorService>()
        OutboundMessageHandler(
            lifecycleCoordinatorFactory,
            configurationReaderService,
            subscriptionFactory,
            SmartConfigImpl.empty(),
            mock {
                on { serialize(any<GatewayMessage>()) } doReturn ByteBuffer.wrap("gateway-message".toByteArray())
            },
            commonComponents,
        ) { mockExecutorService }
        onClose!!.invoke()
        verify(mockExecutorService).shutdown()
    }

    @Test
    fun `onNext will write message to the client and return completed future`() {
        val msgPayload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D"),
                "subsystem",
                "messageId",
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader(
            HoldingIdentity("b", GROUP_ID),
            HoldingIdentity(VALID_X500_NAME, GROUP_ID),
            NetworkType.CORDA_5,
            "https://r3.com/",
        )
        val message = LinkOutMessage(headers, msgPayload)
        whenever(connectionManager.constructed().first().acquire(any())).doReturn(client)

        val future = handler.onNext(Record("", "", message))

        assertThat(sentMessages).hasSize(1)
        assertThat(sentMessages.first()).isEqualTo(serialisedMessage)
        assertThat(future).isCompleted
    }

    @Test
    fun `onNext will complete future if record value is null`() {
        val future = handler.onNext(Record("", "", null))
        assertThat(sentMessages).hasSize(0)
        assertThat(future).isCompleted
    }

    @Test
    fun `onNext will throw an exception if the handler is not ready`() {
        handlerStarted = false
        val payload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D"),
                "subsystem",
                "messageId",
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader(
            HoldingIdentity("b", GROUP_ID),
            HoldingIdentity("a", GROUP_ID),
            NetworkType.CORDA_5,
            "https://r3.com/",
        )
        val message = LinkOutMessage(headers, payload)
        whenever(connectionManager.constructed().first().acquire(any())).doReturn(client)

        assertThrows<IllegalStateException> {
            handler.onNext(Record("", "", message))
        }
    }

    @Test
    fun `onNext will use the correct destination info for CORDA5`() {
        val payload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D"),
                "subsystem",
                "messageId",
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader(
            HoldingIdentity("b", GROUP_ID),
            HoldingIdentity(VALID_X500_NAME, GROUP_ID),
            NetworkType.CORDA_5,
            "https://r3.com/",
        )
        val message = LinkOutMessage(
            headers,
            payload,
        )
        val destinationInfo = argumentCaptor<DestinationInfo>()
        whenever(connectionManager.constructed().first().acquire(destinationInfo.capture())).doReturn(client)

        handler.onNext(Record("", "", message))

        assertThat(destinationInfo.firstValue)
            .isEqualTo(
                DestinationInfo(
                    URI.create("https://r3.com/"),
                    "r3.com",
                    null,
                    truststore,
                    null,
                )
            )
    }

    @Test
    fun `onNext will use the correct key store for mutual TLS`() {
        val sslConfig = mock<SslConfiguration> {
            on { tlsType } doReturn TlsType.MUTUAL
        }
        val keyStore = mock<KeyStoreWithPassword>()
        val dynamicKeyStore = mock<DynamicKeyStore> {
            on {
                getClientKeyStore(
                    HoldingIdentity(VALID_X500_NAME, GROUP_ID),
                )
            } doReturn keyStore
        }
        whenever(commonComponents.dynamicKeyStore).doReturn(dynamicKeyStore)
        whenever(gatewayConfigReader.constructed().first().sslConfiguration).doReturn(sslConfig)
        val payload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D"),
                "subsystem",
                "messageId",
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader(
            HoldingIdentity("b", GROUP_ID),
            HoldingIdentity(VALID_X500_NAME, GROUP_ID),
            NetworkType.CORDA_5,
            "https://r3.com/",
        )
        val message = LinkOutMessage(
            headers,
            payload,
        )
        val destinationInfo = argumentCaptor<DestinationInfo>()
        whenever(connectionManager.constructed().first().acquire(destinationInfo.capture())).doReturn(client)

        handler.onNext(Record("", "", message))

        assertThat(destinationInfo.firstValue)
            .isEqualTo(
                DestinationInfo(
                    URI.create("https://r3.com/"),
                    "r3.com",
                    null,
                    truststore,
                    keyStore,
                )
            )
    }


    @Test
    fun `onNext will do nothing for mutual TLS if the source key store can not be found`() {
        val sslConfig = mock<SslConfiguration> {
            on { tlsType } doReturn TlsType.MUTUAL
        }
        val dynamicKeyStore = mock<DynamicKeyStore> {
            on {
                getClientKeyStore(
                    HoldingIdentity(VALID_X500_NAME, GROUP_ID),
                )
            } doReturn null
        }
        whenever(commonComponents.dynamicKeyStore).doReturn(dynamicKeyStore)
        whenever(gatewayConfigReader.constructed().first().sslConfiguration).doReturn(sslConfig)
        val payload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D"),
                "subsystem",
                "messageId",
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader(
            HoldingIdentity("b", GROUP_ID),
            HoldingIdentity(VALID_X500_NAME, GROUP_ID),
            NetworkType.CORDA_5,
            "https://r3.com/",
        )
        val message = LinkOutMessage(
            headers,
            payload,
        )

        handler.onNext(Record("", "", message))

        verify(connectionManager.constructed().first(), never()).acquire(any())
    }

    @Test
    fun `onNext will use the correct destination info for CORDA4`() {
        val payload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D"),
                "subsystem",
                "messageId",
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader(
            HoldingIdentity("O=PartyB, L=London, C=GB", GROUP_ID),
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

        handler.onNext(Record("", "", message))

        assertThat(destinationInfo.firstValue)
            .isEqualTo(
                DestinationInfo(
                    URI.create("https://r3.com/"),
                    "e7aa0d5c6b562cc528e490d58b7040fe.p2p.corda.net",
                    X500Name("O=PartyB, L=London, C=GB"),
                    truststore,
                    null,
                )
            )
    }

    @Test
    fun `onNext will not send anything for invalid arguments`() {
        val payload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D"),
                "subsystem",
                "messageId",
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader(
            HoldingIdentity("bbb", GROUP_ID),
            HoldingIdentity("aaa", GROUP_ID),
            NetworkType.CORDA_4,
            "https://r3.com/",
        )
        val message = LinkOutMessage(
            headers,
            payload,
        )

        handler.onNext(Record("", "", message))

        verify(connectionManager.constructed().first(), never()).acquire(any())
    }

    @Test
    fun `onNext will not send anything for invalid URL`() {
        val msgPayload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D"),
                "subsystem",
                "messageId",
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader(
            HoldingIdentity("b", GROUP_ID),
            HoldingIdentity(VALID_X500_NAME, GROUP_ID),
            NetworkType.CORDA_5,
            "invalid URL",
        )
        val message = LinkOutMessage(headers, msgPayload)

        handler.onNext(Record("", "", message))

        verify(connectionManager.constructed().first(), never()).acquire(any())
    }

    @Test
    fun `onNext will not send anything for wrong scheme URL`() {
        val msgPayload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D"),
                "subsystem",
                "messageId",
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader(
            HoldingIdentity("b", GROUP_ID),
            HoldingIdentity(VALID_X500_NAME, GROUP_ID),
            NetworkType.CORDA_5,
            "http://www.r3.com:4000/",
        )
        val message = LinkOutMessage(headers, msgPayload)

        handler.onNext(Record("", "", message))

        verify(connectionManager.constructed().first(), never()).acquire(any())
    }

    @Test
    fun `onNext will get the trust store from the trust store map`() {
        val payload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D"),
                "subsystem",
                "messageId",
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader(
            HoldingIdentity("bbb", GROUP_ID),
            HoldingIdentity(VALID_X500_NAME, GROUP_ID),
            NetworkType.CORDA_4,
            "https://r3.com/",
        )

        val message = LinkOutMessage(
            headers,
            payload,
        )

        handler.onNext(Record("", "", message))

        verify(trustStoresMap)
            .getTrustStore(MemberX500Name.parse(VALID_X500_NAME), GROUP_ID)
    }

    @Test
    fun `when message times out, it is retried once`() {
        connectionConfig = ConnectionConfiguration().copy(responseTimeout = 10.millis, retryDelay = 10.millis)
        val client = mock<HttpClient> {
            on { write(any()) } doAnswer {
                sentMessages.add(it.arguments[0] as ByteArray)
                CompletableFuture<HttpResponse>()
                // simulate scenario where no response is received.
            }
        }
        val msgPayload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D"),
                "subsystem",
                "messageId",
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader(
            HoldingIdentity("b", GROUP_ID),
            HoldingIdentity(VALID_X500_NAME, GROUP_ID),
            NetworkType.CORDA_5,
            "https://r3.com/",
        )
        val message = LinkOutMessage(headers, msgPayload)
        whenever(connectionManager.constructed().first().acquire(any())).doReturn(client)

        val recordFuture = handler.onNext(Record("", "", message))
        assertThrows<ExecutionException> { recordFuture.get() }

        mockTimeFacilitiesProvider.advanceTime(connectionConfig.retryDelay)
        assertThat(sentMessages).hasSize(2)
        sentMessages.forEach {
            assertThat(it).isEqualTo(serialisedMessage)
        }

        repeat(2) { mockTimeFacilitiesProvider.advanceTime(connectionConfig.retryDelay.multipliedBy(2)) }
        assertThat(sentMessages).hasSize(2)
    }

    @Test
    fun `when message fails, it is retried once`() {
        connectionConfig = ConnectionConfiguration().copy(responseTimeout = 10.millis, retryDelay = 10.millis)
        val client = mock<HttpClient> {
            on { write(any()) } doAnswer {
                sentMessages.add(it.arguments[0] as ByteArray)
                CompletableFuture.failedFuture(RuntimeException("some error happened"))
            }
        }
        val msgPayload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D"),
                "subsystem",
                "messageId",
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader(
            HoldingIdentity("b", GROUP_ID),
            HoldingIdentity(VALID_X500_NAME, GROUP_ID),
            NetworkType.CORDA_5,
            "https://r3.com/",
        )
        val message = LinkOutMessage(headers, msgPayload)
        whenever(connectionManager.constructed().first().acquire(any())).doReturn(client)

        val record1Future = handler.onNext(Record("", "", message))
        val record2Future = handler.onNext(Record("", "", null))
        assertThrows<ExecutionException> { record1Future.get() }
        record2Future.get()

        mockTimeFacilitiesProvider.advanceTime(connectionConfig.retryDelay)
        assertThat(sentMessages).hasSize(2)
        sentMessages.forEach {
            assertThat(it).isEqualTo(serialisedMessage)
        }

        repeat(2) { mockTimeFacilitiesProvider.advanceTime(connectionConfig.retryDelay.multipliedBy(2)) }
        assertThat(sentMessages).hasSize(2)
    }

    @Test
    fun `when 5xx error code is received, it is retried once`() {
        connectionConfig = ConnectionConfiguration().copy(retryDelay = 10.millis)
        val client = mock<HttpClient> {
            on { write(any()) } doAnswer {
                sentMessages.add(it.arguments[0] as ByteArray)
                val response = mock<HttpResponse> {
                    on { statusCode } doReturn HttpResponseStatus.INTERNAL_SERVER_ERROR
                }
                CompletableFuture.completedFuture(response)
            }
        }
        val msgPayload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D"),
                "subsystem",
                "messageId",
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader(
            HoldingIdentity("b", GROUP_ID),
            HoldingIdentity(VALID_X500_NAME, GROUP_ID),
            NetworkType.CORDA_5,
            "https://r3.com/",
        )
        val message = LinkOutMessage(headers, msgPayload)
        whenever(connectionManager.constructed().first().acquire(any())).doReturn(client)

        handler.onNext(Record("", "", message))
        handler.onNext(Record("", "", null))
        mockTimeFacilitiesProvider.advanceTime(connectionConfig.retryDelay)
        assertThat(sentMessages).hasSize(2)
        sentMessages.forEach {
            assertThat(it).isEqualTo(serialisedMessage)
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
                sentMessages.add(it.arguments[0] as ByteArray)
                val response = mock<HttpResponse> {
                    on { statusCode } doReturn HttpResponseStatus.BAD_REQUEST
                }
                CompletableFuture.completedFuture(response)
            }
        }
        val msgPayload = UnauthenticatedMessage.newBuilder().apply {
            header = UnauthenticatedMessageHeader(
                HoldingIdentity("A", "B"),
                HoldingIdentity("C", "D"),
                "subsystem",
                "messageId",
            )
            payload = ByteBuffer.wrap(byteArrayOf())
        }.build()
        val headers = LinkOutHeader(
            HoldingIdentity("b", GROUP_ID),
            HoldingIdentity(VALID_X500_NAME, GROUP_ID),
            NetworkType.CORDA_5,
            "https://r3.com/",
        )
        val message = LinkOutMessage(headers, msgPayload)
        whenever(connectionManager.constructed().first().acquire(any())).doReturn(client)

        handler.onNext(Record("", "", message))
        handler.onNext(Record("", "", null))

        repeat(2) { mockTimeFacilitiesProvider.advanceTime(connectionConfig.retryDelay.multipliedBy(2)) }
        assertThat(sentMessages).hasSize(1)
        assertThat(sentMessages.first()).isEqualTo(serialisedMessage)
    }
}

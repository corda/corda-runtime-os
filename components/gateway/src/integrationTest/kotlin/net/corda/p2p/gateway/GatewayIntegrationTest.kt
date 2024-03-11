package net.corda.p2p.gateway

import com.typesafe.config.ConfigValueFactory
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.crypto.cipher.suite.schemes.ECDSA_SECP256R1_TEMPLATE
import net.corda.crypto.cipher.suite.schemes.RSA_TEMPLATE
import net.corda.crypto.test.certificates.generation.CertificateAuthority
import net.corda.crypto.test.certificates.generation.CertificateAuthorityFactory
import net.corda.crypto.test.certificates.generation.PrivateKeyWithCertificate
import net.corda.crypto.test.certificates.generation.toFactoryDefinitions
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.GatewayTlsCertificates
import net.corda.data.p2p.GatewayTruststore
import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.LinkOutHeader
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.NetworkType
import net.corda.data.p2p.crypto.AuthenticatedDataMessage
import net.corda.data.p2p.crypto.CommonHeader
import net.corda.data.p2p.crypto.MessageType
import net.corda.data.p2p.gateway.GatewayMessage
import net.corda.data.p2p.gateway.GatewayResponse
import net.corda.data.p2p.linkmanager.LinkManagerResponse
import net.corda.data.p2p.mtls.gateway.ClientCertificateSubjects
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.LifecycleCoordinatorSchedulerFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.processor.SyncRPCProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.config.SyncRPCConfig
import net.corda.messaging.emulation.EmulatorFactory
import net.corda.p2p.gateway.messaging.ConnectionConfiguration
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import net.corda.p2p.gateway.messaging.GatewayServerConfiguration
import net.corda.p2p.gateway.messaging.RevocationConfig
import net.corda.p2p.gateway.messaging.RevocationConfigMode
import net.corda.p2p.gateway.messaging.SslConfiguration
import net.corda.p2p.gateway.messaging.TlsType
import net.corda.p2p.gateway.messaging.http.DestinationInfo
import net.corda.p2p.gateway.messaging.http.HttpClient
import net.corda.p2p.gateway.messaging.http.HttpRequest
import net.corda.p2p.gateway.messaging.http.HttpServer
import net.corda.p2p.gateway.messaging.http.HttpWriter
import net.corda.p2p.gateway.messaging.http.KeyStoreWithPassword
import net.corda.p2p.gateway.messaging.http.SniCalculator
import net.corda.p2p.gateway.messaging.http.TrustStoresMap
import net.corda.p2p.gateway.messaging.internal.RequestListener
import net.corda.schema.Schemas
import net.corda.schema.Schemas.P2P.GATEWAY_ALLOWED_CLIENT_CERTIFICATE_SUBJECTS
import net.corda.schema.Schemas.P2P.GATEWAY_TLS_TRUSTSTORES
import net.corda.schema.Schemas.P2P.LINK_IN_TOPIC
import net.corda.schema.Schemas.P2P.LINK_OUT_TOPIC
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.BootConfig.TOPIC_PREFIX
import net.corda.schema.configuration.MessagingConfig.MAX_ALLOWED_MSG_SIZE
import net.corda.schema.registry.deserialize
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl
import net.corda.test.util.eventually
import net.corda.test.util.lifecycle.usingLifecycle
import net.corda.utilities.concurrent.getOrThrow
import net.corda.utilities.flags.Features
import net.corda.utilities.seconds
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.EncodingUtils.toHex
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIterable
import org.bouncycastle.jce.PrincipalUtil
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory
import java.io.StringWriter
import java.net.ConnectException
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import java.net.Socket
import java.net.URI
import java.net.http.HttpResponse.BodyHandlers
import java.nio.ByteBuffer
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import java.net.http.HttpClient as JavaHttpClient
import java.net.http.HttpRequest as JavaHttpRequest

internal class GatewayIntegrationTest : TestBase() {
    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val GROUP_ID = "Group - 1"

        const val aliceX500name = "CN=Alice, O=Alice Corp, L=LDN, C=GB"
        const val bobX500Name = "CN=Bob, O=Bob Corp, L=LDN, C=GB"
        const val MAX_REQUEST_SIZE = 50_000_000L
        private val aliceHoldingIdentity = HoldingIdentity(aliceX500name, GROUP_ID)
        private val bobHoldingIdentity = HoldingIdentity(bobX500Name, GROUP_ID)
    }

    private val sessionId = "session-1"
    private val instanceId = AtomicInteger(0)

    private val baseMessagingConfig = SmartConfigImpl.empty()
        .withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(instanceId.getAndIncrement()))
        .withValue(MAX_ALLOWED_MSG_SIZE, ConfigValueFactory.fromAnyRef(10000000))
    private fun messagingConfig() =
        baseMessagingConfig
            .withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(instanceId.incrementAndGet()))

    private val avroSchemaRegistry = AvroSchemaRegistryImpl()
    private val platformInfoProvider = object : PlatformInfoProvider {
        override val activePlatformVersion = 1
        override val localWorkerPlatformVersion = 1
        override val localWorkerSoftwareVersion = "5.2"
    }
    private val bootConfig = SmartConfigImpl.empty()
        .withValue(
            BootConfig.P2P_LINK_MANAGER_WORKER_REST_ENDPOINT,
            ConfigValueFactory.fromAnyRef("localhost:${getOpenPort()}"),
        )

    private inner class Node(private val name: String) {
        val lifecycleCoordinatorFactory =
            LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl(), LifecycleCoordinatorSchedulerFactoryImpl())
        private val emulator = EmulatorFactory.create(
            lifecycleCoordinatorFactory
        ).also {
            keep(it)
        }

        val subscriptionFactory = emulator.subscriptionFactory
        val publisherFactory = emulator.publisherFactory
        val publisher = publisherFactory.createPublisher(PublisherConfig("$name.id", false), baseMessagingConfig)
        val cryptoOpsClient = TestCryptoOpsClient(lifecycleCoordinatorFactory)

        fun stop() {
            publisher.close()
        }

        fun publish(vararg records: Record<Any, Any>): List<CompletableFuture<Unit>> {
            return publisher.publish(records.toList())
        }

        fun getLinkInMessages(size: Int): Collection<LinkInMessage> {
            return if (Features().enableP2PGatewayToLinkManagerOverHttp) {
                while (sentToLinkManager.size < size) {
                    lock.withLock {
                        newMessageNotification.await(1, TimeUnit.SECONDS)
                    }
                }
                sentToLinkManager
            } else {
                getRecords(LINK_IN_TOPIC, size)
                    .mapNotNull {
                        it.value as? LinkInMessage
                    }
            }
        }

        private fun getRecords(topic: String, size: Int): Collection<EventLogRecord<Any, Any>> {
            val stop = CountDownLatch(size)
            val records = ConcurrentHashMap.newKeySet<EventLogRecord<Any, Any>>()
            subscriptionFactory.createEventLogSubscription(
                subscriptionConfig = SubscriptionConfig("group.$name", topic),
                processor = object : EventLogProcessor<Any, Any> {
                    override fun onNext(events: List<EventLogRecord<Any, Any>>): List<Record<*, *>> {
                        events.forEach {
                            records.add(it)
                            stop.countDown()
                        }

                        return emptyList()
                    }

                    override val keyClass = Any::class.java
                    override val valueClass = Any::class.java
                },
                messagingConfig = messagingConfig()
                    .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef("")),
                partitionAssignmentListener = null
            ).use {
                it.start()
                stop.await()
            }
            return records
        }
        fun publishKeyStoreCertificatesAndKeys(
            keyStoreWithPassword: KeyStoreWithPassword,
            holdingIdentity: HoldingIdentity,
            key: String? = null,
        ) {
            val tenantId = "tenantId"
            val records = keyStoreWithPassword.keyStore.aliases().toList().flatMap { alias ->
                val certificateChain = keyStoreWithPassword.keyStore.getCertificateChain(alias)
                val pems = certificateChain.map { certificate ->
                    StringWriter().use { str ->
                        JcaPEMWriter(str).use { writer ->
                            writer.writeObject(certificate)
                        }
                        str.toString()
                    }
                }
                val name = key ?: PrincipalUtil.getSubjectX509Principal(certificateChain.first() as X509Certificate).name
                val certificateRecord = Record(
                    Schemas.P2P.GATEWAY_TLS_CERTIFICATES,
                    name,
                    GatewayTlsCertificates(tenantId, holdingIdentity, pems)
                )

                listOf(certificateRecord)
            }

            publisher.publish(records).forEach {
                it.join()
            }

            cryptoOpsClient.createTenantKeys(keyStoreWithPassword, tenantId)
        }
        fun allowCertificates(
            keyStoreWithPassword: KeyStoreWithPassword,
        ) {
            val records = keyStoreWithPassword.keyStore.aliases().toList().flatMap { alias ->
                keyStoreWithPassword.keyStore.getCertificateChain(alias).toList()
            }.filterIsInstance<X509Certificate>()
                .map { it.subjectX500Principal }
                .map { MemberX500Name.build(it).toString() }
                .map {
                    Record(
                        GATEWAY_ALLOWED_CLIENT_CERTIFICATE_SUBJECTS,
                        it,
                        ClientCertificateSubjects(it)
                    )
                }

            publisher.publish(records).forEach {
                it.join()
            }
        }

        private val sentToLinkManager = ConcurrentLinkedDeque<LinkInMessage>()
        private val lock = ReentrantLock()
        private val newMessageNotification = lock.newCondition()
        fun listenToLinkManagerRpc() {
            val config = SyncRPCConfig(
                name = UUID.randomUUID().toString(),
                endpoint = "/p2p-link-manager"
            )

            val processor = object : SyncRPCProcessor<LinkInMessage, LinkManagerResponse> {
                override fun process(request: LinkInMessage): LinkManagerResponse {
                    sentToLinkManager.push(request)
                    lock.withLock {
                        newMessageNotification.signalAll()
                    }
                    return LinkManagerResponse(null)
                }

                override val requestClass = LinkInMessage::class.java
                override val responseClass = LinkManagerResponse::class.java

            }


            emulator.subscriptionFactory.createHttpRPCSubscription(
                config,
                processor,
            ).start()
        }
    }

    private val alice = Node("alice").also { it.listenToLinkManagerRpc() }
    private val bob = Node("bob").also { it.listenToLinkManagerRpc() }

    @AfterEach
    fun setup() {
        alice.stop()
        bob.stop()
    }

    @Nested
    inner class ClientToGatewayTests {
        @Test
        @Timeout(30)
        fun `gateway response to invalid request`() {
            val port = getOpenPort()
            val serverAddress = URI.create("https://www.alice.net:$port")

            val tmf = TrustManagerFactory
                .getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(truststoreKeyStore.trustStore)

            val myTm = tmf.trustManagers.filterIsInstance(X509TrustManager::class.java).first()

            val sslContext = SSLContext.getInstance("TLSv1.3")
            sslContext.init(null, arrayOf(myTm), null)

            Gateway(
                createConfigurationServiceFor(
                    GatewayConfiguration(
                        listOf(
                            GatewayServerConfiguration(
                                serverAddress.host,
                                serverAddress.port,
                                "/",
                            )
                        ),
                        aliceSslConfig,
                        MAX_REQUEST_SIZE
                    ),
                ),
                alice.subscriptionFactory,
                alice.publisherFactory,
                alice.lifecycleCoordinatorFactory,
                alice.cryptoOpsClient,
                avroSchemaRegistry,
                platformInfoProvider,
                bootConfig,
                messagingConfig(),
            ).usingLifecycle {
                alice.publishKeyStoreCertificatesAndKeys(aliceKeyStore, aliceHoldingIdentity)
                it.startAndWaitForStarted()
                val httpClient = JavaHttpClient.newBuilder()
                    .sslContext(sslContext)
                    .build()

                val request = JavaHttpRequest.newBuilder()
                    .POST(java.net.http.HttpRequest.BodyPublishers.noBody())
                    .uri(URI("$serverAddress"))
                    .header("Content-type", "application/json")
                    .build()

                val response = httpClient.send(request, BodyHandlers.discarding())
                assertThat(response.statusCode()).isEqualTo(HTTP_BAD_REQUEST)
            }
        }

        @Test
        @Timeout(30)
        fun `http client to gateway`() {
            val port = getOpenPort()
            val serverAddress = URI.create("https://www.alice.net:$port")
            val linkInMessage = LinkInMessage(authenticatedP2PMessage(""))
            val gatewayMessage = GatewayMessage("msg-id", linkInMessage.payload)
            Gateway(
                createConfigurationServiceFor(
                    GatewayConfiguration(
                        listOf(
                            GatewayServerConfiguration(
                                serverAddress.host,
                                serverAddress.port,
                                "/",
                            )
                        ),
                        aliceSslConfig,
                        MAX_REQUEST_SIZE
                    ),
                ),
                alice.subscriptionFactory,
                alice.publisherFactory,
                alice.lifecycleCoordinatorFactory,
                alice.cryptoOpsClient,
                avroSchemaRegistry,
                platformInfoProvider,
                bootConfig,
                messagingConfig(),
            ).usingLifecycle {
                alice.publishKeyStoreCertificatesAndKeys(aliceKeyStore, aliceHoldingIdentity)
                it.startAndWaitForStarted()
                val serverInfo = DestinationInfo(serverAddress, aliceSNI[0], null, truststoreKeyStore, null)
                HttpClient(
                    serverInfo,
                    bobSslConfig,
                    NioEventLoopGroup(1),
                    NioEventLoopGroup(1),
                    ConnectionConfiguration(),
                ).use { client ->
                    client.start()
                    val httpResponse = client.write(avroSchemaRegistry.serialize(gatewayMessage).array()).get()
                    assertThat(httpResponse.statusCode).isEqualTo(HttpResponseStatus.OK)
                    assertThat(httpResponse.payload).isNotNull
                    val gatewayResponse = avroSchemaRegistry.deserialize<GatewayResponse>(ByteBuffer.wrap(httpResponse.payload))
                    assertThat(gatewayResponse.id).isEqualTo(gatewayMessage.id)
                }
            }

            // Verify Gateway has successfully forwarded the message to the P2P_IN topic
            val publishedRecords = alice.getLinkInMessages(1)
            assertThatIterable(publishedRecords)
                .hasSize(1).allSatisfy {
                        assertThat(it.payload).isInstanceOfSatisfying(AuthenticatedDataMessage::class.java) {
                            assertThat(it).isEqualTo(linkInMessage.payload)
                        }
                    }
        }

        @Test
        @Timeout(30)
        fun `http client to gateway with a few servers`() {
            val serverCount = 5
            val ports = (1..serverCount).map {
                getOpenPort()
            }
            val serversAddresses = ports.map { port ->
                URI.create("https://www.alice.net:$port/$port/")
            }
            val serversConfigurations = serversAddresses.map {  serverAddress ->
                GatewayServerConfiguration(
                    serverAddress.host,
                    serverAddress.port,
                    serverAddress.path,
                )
            }
            val gatewayMessages = serversAddresses.associateWith { url ->
                val linkInMessage = LinkInMessage(authenticatedP2PMessage(url.toString()))
                GatewayMessage(url.toString(), linkInMessage.payload)
            }
            Gateway(
                createConfigurationServiceFor(
                    GatewayConfiguration(
                        serversConfigurations,
                        aliceSslConfig,
                        MAX_REQUEST_SIZE
                    ),
                ),
                alice.subscriptionFactory,
                alice.publisherFactory,
                alice.lifecycleCoordinatorFactory,
                alice.cryptoOpsClient,
                avroSchemaRegistry,
                platformInfoProvider,
                bootConfig,
                messagingConfig(),
            ).usingLifecycle {
                alice.publishKeyStoreCertificatesAndKeys(aliceKeyStore, aliceHoldingIdentity)
                it.startAndWaitForStarted()
                gatewayMessages.forEach { (serverAddress, gatewayMessage) ->
                    val serverInfo = DestinationInfo(serverAddress, aliceSNI[0], null, truststoreKeyStore, null)
                    HttpClient(
                        serverInfo,
                        bobSslConfig,
                        NioEventLoopGroup(1),
                        NioEventLoopGroup(1),
                        ConnectionConfiguration(),
                    ).use { client ->
                        client.start()
                        val httpResponse = client.write(avroSchemaRegistry.serialize(gatewayMessage).array()).get()
                        assertThat(httpResponse.statusCode).isEqualTo(HttpResponseStatus.OK)
                        assertThat(httpResponse.payload).isNotNull
                        val gatewayResponse = avroSchemaRegistry.deserialize<GatewayResponse>(ByteBuffer.wrap(httpResponse.payload))
                        assertThat(gatewayResponse.id).isEqualTo(gatewayMessage.id)
                    }
                }
            }

            // Verify Gateway has successfully forwarded the message to the P2P_IN topic
            val publishedRecords = alice.getLinkInMessages(serverCount).mapNotNull {
                it.payload as? AuthenticatedDataMessage
            }.map {
                it.payload
            }.map {
                String(it.array())
            }
            assertThat(publishedRecords)
                .hasSize(serverCount)
                .containsExactlyInAnyOrderElementsOf(gatewayMessages.map { it.key.toString() })
        }


        @Test
        @Timeout(30)
        fun `http client to gateway with a few paths`() {
            val pathsCount = 7
            val paths = (1..pathsCount).map {
                "path/$it"
            }
            val port = getOpenPort()
            val serversAddresses = paths.map { path ->
                URI.create("https://www.alice.net:$port/$path/")
            }
            val serversConfigurations = serversAddresses.map {  serverAddress ->
                GatewayServerConfiguration(
                    serverAddress.host,
                    serverAddress.port,
                    serverAddress.path,
                )
            }
            val gatewayMessages = serversAddresses.associateWith { url ->
                val linkInMessage = LinkInMessage(authenticatedP2PMessage(url.toString()))
                GatewayMessage(url.toString(), linkInMessage.payload)
            }
            Gateway(
                createConfigurationServiceFor(
                    GatewayConfiguration(
                        serversConfigurations,
                        aliceSslConfig,
                        MAX_REQUEST_SIZE
                    ),
                ),
                alice.subscriptionFactory,
                alice.publisherFactory,
                alice.lifecycleCoordinatorFactory,
                alice.cryptoOpsClient,
                avroSchemaRegistry,
                platformInfoProvider,
                bootConfig,
                messagingConfig(),
            ).usingLifecycle {
                alice.publishKeyStoreCertificatesAndKeys(aliceKeyStore, aliceHoldingIdentity)
                it.startAndWaitForStarted()
                gatewayMessages.forEach { (serverAddress, gatewayMessage) ->
                    val serverInfo = DestinationInfo(serverAddress, aliceSNI[0], null, truststoreKeyStore, null)
                    HttpClient(
                        serverInfo,
                        bobSslConfig,
                        NioEventLoopGroup(1),
                        NioEventLoopGroup(1),
                        ConnectionConfiguration(),
                    ).use { client ->
                        client.start()
                        val httpResponse = client.write(avroSchemaRegistry.serialize(gatewayMessage).array()).get()
                        assertThat(httpResponse.statusCode).isEqualTo(HttpResponseStatus.OK)
                        assertThat(httpResponse.payload).isNotNull
                        val gatewayResponse = avroSchemaRegistry.deserialize<GatewayResponse>(ByteBuffer.wrap(httpResponse.payload))
                        assertThat(gatewayResponse.id).isEqualTo(gatewayMessage.id)
                    }
                }
            }

            // Verify Gateway has successfully forwarded the message to the P2P_IN topic
            val publishedRecords = alice.getLinkInMessages(pathsCount).mapNotNull {
                it.payload as? AuthenticatedDataMessage
            }.map {
                it.payload
            }.map {
                String(it.array())
            }
            assertThat(publishedRecords)
                .hasSize(pathsCount)
                .containsExactlyInAnyOrderElementsOf(gatewayMessages.map { it.key.toString() })
        }

        @Test
        @Timeout(30)
        fun `http client to gateway after changing URL`() {
            val serverConfigurationOne = GatewayServerConfiguration(
                "www.alice.net",
                getOpenPort(),
                "/url/one",
            )
            val serverConfigurationTwo = serverConfigurationOne.copy(urlPaths = setOf("/url/two"))
            val linkInMessageOne = LinkInMessage(authenticatedP2PMessage("one"))
            val messageOne = GatewayMessage("one", linkInMessageOne.payload)
            val linkInMessageTwo = LinkInMessage(authenticatedP2PMessage("two"))
            val messageTwo = GatewayMessage("two", linkInMessageTwo.payload)
            val configPublisher = ConfigPublisher()
            keep(configPublisher)
            configPublisher.publishConfig(
                GatewayConfiguration(
                    listOf(serverConfigurationOne),
                    aliceSslConfig,
                    MAX_REQUEST_SIZE,
                ),
            )
            Gateway(
                configPublisher.readerService,
                alice.subscriptionFactory,
                alice.publisherFactory,
                alice.lifecycleCoordinatorFactory,
                alice.cryptoOpsClient,
                avroSchemaRegistry,
                platformInfoProvider,
                bootConfig,
                messagingConfig(),
            ).usingLifecycle {
                alice.publishKeyStoreCertificatesAndKeys(aliceKeyStore, aliceHoldingIdentity)
                it.startAndWaitForStarted()
                val serverAddress = URI.create(
                    "https://${serverConfigurationOne.hostAddress}:" +
                        serverConfigurationOne.hostPort +
                        serverConfigurationOne.urlPaths.first(),
                )
                val serverInfo = DestinationInfo(serverAddress, aliceSNI[0], null, truststoreKeyStore, null)
                HttpClient(
                    serverInfo,
                    bobSslConfig,
                    NioEventLoopGroup(1),
                    NioEventLoopGroup(1),
                    ConnectionConfiguration(),
                ).use { client ->
                    client.start()
                    val httpResponse = client.write(avroSchemaRegistry.serialize(messageOne).array()).get()
                    assertThat(httpResponse.statusCode).isEqualTo(HttpResponseStatus.OK)
                    assertThat(httpResponse.payload).isNotNull
                    val gatewayResponse =
                        avroSchemaRegistry.deserialize<GatewayResponse>(ByteBuffer.wrap(httpResponse.payload))
                    assertThat(gatewayResponse.id).isEqualTo(messageOne.id)
                }

                configPublisher.publishConfig(
                    GatewayConfiguration(
                        listOf(serverConfigurationTwo),
                        aliceSslConfig,
                        MAX_REQUEST_SIZE,
                    ),
                )

                eventually(
                    duration = 20.seconds,
                    retryAllExceptions = true,
                ) {
                    val serverTwoAddress = URI.create(
                        "https://${serverConfigurationTwo.hostAddress}:" +
                            serverConfigurationTwo.hostPort +
                            serverConfigurationTwo.urlPaths.first(),
                    )
                    val serverTwoInfo = DestinationInfo(serverTwoAddress, aliceSNI[0], null, truststoreKeyStore, null)
                    HttpClient(
                        serverTwoInfo,
                        bobSslConfig,
                        NioEventLoopGroup(1),
                        NioEventLoopGroup(1),
                        ConnectionConfiguration(),
                    ).use { client ->
                        client.start()
                        val httpResponse = client.write(avroSchemaRegistry.serialize(messageTwo).array()).get()
                        assertThat(httpResponse.statusCode).isEqualTo(HttpResponseStatus.OK)
                        assertThat(httpResponse.payload).isNotNull
                        val gatewayResponse =
                            avroSchemaRegistry.deserialize<GatewayResponse>(ByteBuffer.wrap(httpResponse.payload))
                        assertThat(gatewayResponse.id).isEqualTo(messageTwo.id)
                    }
                }
            }

            // Verify Gateway has successfully forwarded the messages to the P2P_IN topic
            val publishedRecords = alice.getLinkInMessages(2).mapNotNull {
                it.payload as? AuthenticatedDataMessage
            }.map {
                it.payload
            }.map {
                String(it.array())
            }
            assertThat(publishedRecords)
                .hasSize(2)
                .contains(messageTwo.id, messageOne.id)
        }

        @Test
        @Timeout(30)
        fun `requests with extremely large payloads are rejected`() {
            val port = getOpenPort()
            val serverAddress = URI.create("https://www.alice.net:$port")
            val bigMessage = ByteArray(10_000_000)
            val linkInMessage = LinkInMessage(authenticatedP2PMessage(toHex(bigMessage)))
            val gatewayMessage = GatewayMessage("msg-id", linkInMessage.payload)
            Gateway(
                createConfigurationServiceFor(
                    GatewayConfiguration(
                        listOf(
                            GatewayServerConfiguration(
                                serverAddress.host,
                                serverAddress.port,
                                "/",
                            )
                        ),
                        aliceSslConfig,
                        MAX_REQUEST_SIZE
                    ),
                ),
                alice.subscriptionFactory,
                alice.publisherFactory,
                alice.lifecycleCoordinatorFactory,
                alice.cryptoOpsClient,
                avroSchemaRegistry,
                platformInfoProvider,
                bootConfig,
                messagingConfig(),
            ).usingLifecycle {
                alice.publishKeyStoreCertificatesAndKeys(aliceKeyStore, aliceHoldingIdentity)
                it.startAndWaitForStarted()
                val serverInfo = DestinationInfo(serverAddress, aliceSNI[0], null, truststoreKeyStore, null)
                HttpClient(
                    serverInfo,
                    bobSslConfig,
                    NioEventLoopGroup(1),
                    NioEventLoopGroup(1),
                    ConnectionConfiguration(),
                ).use { client ->
                    client.start()
                    val httpResponse =  client.write(avroSchemaRegistry.serialize(gatewayMessage).array()).get()
                    assertThat(httpResponse.statusCode).isEqualTo(HttpResponseStatus.BAD_REQUEST)
                }
            }
        }

        @Test
        @Timeout(30)
        fun `http client to gateway with ip address`() {
            val port = getOpenPort()
            val ipAddress = "127.0.0.1"
            val serverAddress = URI.create("https://$ipAddress:$port")
            val linkInMessage = LinkInMessage(authenticatedP2PMessage(""))
            val gatewayMessage = GatewayMessage("msg-id", linkInMessage.payload)
            Gateway(
                createConfigurationServiceFor(
                    GatewayConfiguration(
                        listOf(
                            GatewayServerConfiguration(
                                serverAddress.host,
                                serverAddress.port,
                                "/",
                            )
                        ),
                        aliceSslConfig,
                        MAX_REQUEST_SIZE
                    ),
                ),
                alice.subscriptionFactory,
                alice.publisherFactory,
                alice.lifecycleCoordinatorFactory,
                alice.cryptoOpsClient,
                avroSchemaRegistry,
                platformInfoProvider,
                bootConfig,
                messagingConfig(),
            ).usingLifecycle {
                alice.publishKeyStoreCertificatesAndKeys(ipKeyStore, aliceHoldingIdentity)
                it.startAndWaitForStarted()
                val serverInfo = DestinationInfo(
                    serverAddress,
                    SniCalculator.calculateCorda5Sni(serverAddress),
                    null,
                    truststoreKeyStore,
                    null,
                )
                HttpClient(
                    serverInfo,
                    bobSslConfig,
                    NioEventLoopGroup(1),
                    NioEventLoopGroup(1),
                    ConnectionConfiguration(),
                ).use { client ->
                    client.start()
                    val httpResponse = client.write(avroSchemaRegistry.serialize(gatewayMessage).array()).get()
                    assertThat(httpResponse.statusCode).isEqualTo(HttpResponseStatus.OK)
                    assertThat(httpResponse.payload).isNotNull
                    val gatewayResponse =
                        avroSchemaRegistry.deserialize<GatewayResponse>(ByteBuffer.wrap(httpResponse.payload))
                    assertThat(gatewayResponse.id).isEqualTo(gatewayMessage.id)
                }
            }

            // Verify Gateway has successfully forwarded the message to the P2P_IN topic
            val publishedRecords = alice.getLinkInMessages(1)
            assertThatIterable(publishedRecords)
                .hasSize(1).allSatisfy {
                    assertThat(it.payload).isInstanceOfSatisfying(AuthenticatedDataMessage::class.java) {
                        assertThat(it).isEqualTo(linkInMessage.payload)
                    }
                }
        }
    }

    @Nested
    @Suppress("ForEachOnRange")
    inner class ReconfigurationTests {
        @Test
        @Timeout(100)
        fun `gateway reconfiguration`() {
            val configurationCount = 3
            alice.publish(
                Record(GATEWAY_TLS_TRUSTSTORES, "$aliceX500name-$GROUP_ID",
                    GatewayTruststore(aliceHoldingIdentity, listOf(truststoreCertificatePem)))
            )
            val recipientServerUrl = URI.create("https://www.alice.net:${getOpenPort()}")

            val linkInMessage = LinkInMessage(authenticatedP2PMessage(""))
            val linkOutMessage = LinkOutMessage.newBuilder().apply {
                header = LinkOutHeader(
                    bobHoldingIdentity,
                    aliceHoldingIdentity,
                    NetworkType.CORDA_5,
                    recipientServerUrl.toString(),
                )
                payload = authenticatedP2PMessage("link out")
            }.build()
            val gatewayMessage = GatewayMessage("msg-id", linkInMessage.payload)

            val configPublisher = ConfigPublisher()
            keep(configPublisher)

            val messageReceivedLatch = AtomicReference(CountDownLatch(1))
            val listenToOutboundMessages = object : RequestListener {
                override fun onRequest(httpWriter: HttpWriter, request: HttpRequest) {
                    val receivedGatewayMessage = avroSchemaRegistry.deserialize<GatewayMessage>(ByteBuffer.wrap(request.payload))
                    val p2pMessage = LinkInMessage(receivedGatewayMessage.payload)
                    assertThat(p2pMessage.payload).isInstanceOfSatisfying(AuthenticatedDataMessage::class.java) {
                        assertThat(String(it.payload.array())).isEqualTo("link out")
                    }
                    httpWriter.write(HttpResponseStatus.OK, request.source)
                    messageReceivedLatch.get().countDown()
                }
            }
            HttpServer(
                listenToOutboundMessages,
                MAX_REQUEST_SIZE,
                GatewayServerConfiguration(
                    recipientServerUrl.host,
                    recipientServerUrl.port,
                    "/",
                ),
                aliceKeyStore,
                null,
            ).use { recipientServer ->
                alice.publishKeyStoreCertificatesAndKeys(aliceKeyStore, aliceHoldingIdentity)
                recipientServer.startAndWaitForStarted()
                Gateway(
                    configPublisher.readerService,
                    alice.subscriptionFactory,
                    alice.publisherFactory,
                    alice.lifecycleCoordinatorFactory,
                    alice.cryptoOpsClient,
                    avroSchemaRegistry,
                    platformInfoProvider,
                    bootConfig,
                    messagingConfig(),
                ).usingLifecycle { gateway ->
                    gateway.start()

                    (1..configurationCount).map {
                        getOpenPort()
                    }.map {
                        URI.create("https://www.alice.net:$it")
                    }.forEach { url ->
                        configPublisher.publishConfig(GatewayConfiguration(
                            listOf(GatewayServerConfiguration(url.host, url.port, "/",)),
                            aliceSslConfig, MAX_REQUEST_SIZE))
                        eventually(duration = 20.seconds) {
                            assertThat(gateway.isRunning).isTrue
                        }
                        eventually(
                            duration = 10.seconds,
                            waitBefore = Duration.ofMillis(200),
                            waitBetween = Duration.ofMillis(200)
                        ) {
                            assertDoesNotThrow {
                                Socket(url.host, url.port).close()
                            }
                        }

                        HttpClient(
                            DestinationInfo(
                                url,
                                aliceSNI[0],
                                null,
                                truststoreKeyStore,
                                null,
                            ),
                            aliceSslConfig,
                            NioEventLoopGroup(1),
                            NioEventLoopGroup(1),
                            ConnectionConfiguration(),
                        ).use { secondInboundClient ->
                            secondInboundClient.start()

                            val httpResponse = secondInboundClient.write(avroSchemaRegistry.serialize(gatewayMessage).array()).get()
                            assertThat(httpResponse.statusCode).isEqualTo(HttpResponseStatus.OK)
                            assertThat(httpResponse.payload).isNotNull
                            val gatewayResponse = avroSchemaRegistry.deserialize<GatewayResponse>(ByteBuffer.wrap(httpResponse.payload))
                            assertThat(gatewayResponse.id).isEqualTo(gatewayMessage.id)
                        }

                        messageReceivedLatch.set(CountDownLatch(1))
                        alice.publish(Record(LINK_OUT_TOPIC, "key", linkOutMessage))
                        messageReceivedLatch.get().await()
                    }
                }
            }
        }
    }

    @Nested
    @Suppress("ForEachOnRange") // TODO - fix this
    inner class MultipleClientsToGatewayTests {
        @Test
        @Timeout(60)
        fun `multiple clients to gateway`() {
            val msgNumber = AtomicInteger(1)
            val clientNumber = 4
            val threadPool = NioEventLoopGroup(clientNumber)
            val serverAddress = URI.create("https://www.alice.net:${getOpenPort()}")
            alice.publishKeyStoreCertificatesAndKeys(aliceKeyStore, aliceHoldingIdentity)
            Gateway(
                createConfigurationServiceFor(
                    GatewayConfiguration(
                        listOf(
                            GatewayServerConfiguration(
                                serverAddress.host,
                                serverAddress.port,
                                "/",
                            )
                        ),
                        aliceSslConfig,
                        MAX_REQUEST_SIZE
                    )
                ),
                alice.subscriptionFactory,
                alice.publisherFactory,
                alice.lifecycleCoordinatorFactory,
                alice.cryptoOpsClient,
                avroSchemaRegistry,
                platformInfoProvider,
                bootConfig,
                messagingConfig(),
            ).usingLifecycle {
                it.startAndWaitForStarted()
                (1..clientNumber).map { index ->
                    val serverInfo =
                        DestinationInfo(
                            serverAddress, aliceSNI[1], null, truststoreKeyStore, null)
                    val client =
                        HttpClient(serverInfo, bobSslConfig, threadPool, threadPool, ConnectionConfiguration())
                    client.start()
                    val p2pOutMessage = LinkInMessage(authenticatedP2PMessage("Client-$index"))
                    val gatewayMessage = GatewayMessage("msg-${msgNumber.getAndIncrement()}", p2pOutMessage.payload)
                    val future = client.write(avroSchemaRegistry.serialize(gatewayMessage).array())
                    Triple(client, gatewayMessage, future)
                }.forEach { (client, gatewayMessage, future) ->
                    val httpResponse = future.get()
                    assertThat(httpResponse.statusCode).isEqualTo(HttpResponseStatus.OK)
                    assertThat(httpResponse.payload).isNotNull
                    val gatewayResponse =
                        avroSchemaRegistry.deserialize<GatewayResponse>(ByteBuffer.wrap(httpResponse.payload))
                    assertThat(gatewayResponse.id).isEqualTo(gatewayMessage.id)
                    client.close()
                }
            }

            // Verify Gateway has received all [clientNumber] messages and that they were forwarded to the P2P_IN topic
            val publishedRecords = alice.getLinkInMessages(clientNumber)
                .asSequence()
                .map { it.payload }
                .filterIsInstance<AuthenticatedDataMessage>()
                .map { it.payload.array() }
                .map { String(it) }
                .map { it.substringAfter("Client-") }
                .map { it.toInt() }
                .toList()
            assertThat(publishedRecords).hasSize(clientNumber).contains(1, 2, 3, 4)
        }
    }

    @Nested
    inner class GatewayToMultipleServersTest {
        @Test
        @Timeout(60)
        fun `gateway to multiple servers`() {
            val messageCount = 100
            val serversCount = 4
            alice.publish(
                Record(GATEWAY_TLS_TRUSTSTORES, "$aliceX500name-$GROUP_ID",
                    GatewayTruststore(aliceHoldingIdentity, listOf(truststoreCertificatePem)))
            )

            // We first produce some messages which will be consumed by the Gateway.
            val deliveryLatch = CountDownLatch(serversCount * messageCount)
            val serverUrls = (1..serversCount).map {
                getOpenPort()
            }.map {
                "https://www.chip.net:$it"
            }
            val servers = serverUrls.map { serverUrl ->
                URI.create(serverUrl)
            }.map { serverUri ->
                val serverListener = object : RequestListener {
                    override fun onRequest(httpWriter: HttpWriter, request: HttpRequest) {
                        val gatewayMessage = avroSchemaRegistry.deserialize<GatewayMessage>(ByteBuffer.wrap(request.payload))
                        val p2pMessage = LinkInMessage(gatewayMessage.payload)
                        assertThat(
                            String((p2pMessage.payload as AuthenticatedDataMessage).payload.array())
                        )
                            .isEqualTo("Target-$serverUri")
                        val gatewayResponse = GatewayResponse(gatewayMessage.id, null)
                        httpWriter.write(HttpResponseStatus.OK, request.source, avroSchemaRegistry.serialize(gatewayResponse).array())
                        deliveryLatch.countDown()
                    }
                }
                HttpServer(
                    serverListener,
                    MAX_REQUEST_SIZE,
                    GatewayServerConfiguration(serverUri.host, serverUri.port, "/"),
                    chipKeyStore,
                    null,
                )
            }.onEach {
                it.startAndWaitForStarted()
            }

            var startTime: Long = 0
            var endTime: Long = 0
            val gatewayAddress = Pair("localhost", getOpenPort())
            Gateway(
                createConfigurationServiceFor(
                    GatewayConfiguration(
                        listOf(
                            GatewayServerConfiguration(
                                gatewayAddress.first,
                                gatewayAddress.second,
                                "/",
                            )
                        ),
                        aliceSslConfig,
                        MAX_REQUEST_SIZE
                    )
                ),
                alice.subscriptionFactory,
                alice.publisherFactory,
                alice.lifecycleCoordinatorFactory,
                alice.cryptoOpsClient,
                avroSchemaRegistry,
                platformInfoProvider,
                bootConfig,
                messagingConfig(),
            ).usingLifecycle {
                alice.publishKeyStoreCertificatesAndKeys(aliceKeyStore, aliceHoldingIdentity)
                startTime = Instant.now().toEpochMilli()
                it.startAndWaitForStarted()
                serverUrls.forEach { url ->
                    repeat(messageCount) {
                        val msg = LinkOutMessage.newBuilder().apply {
                            header = LinkOutHeader(
                                bobHoldingIdentity,
                                aliceHoldingIdentity,
                                NetworkType.CORDA_5,
                                url,
                            )
                            payload = authenticatedP2PMessage("Target-$url")
                        }.build()
                        alice.publish(Record(LINK_OUT_TOPIC, "key", msg))
                    }
                }

                // Wait until all messages have been delivered
                deliveryLatch.await(1, TimeUnit.MINUTES)
                endTime = Instant.now().toEpochMilli()
            }

            logger.info("Done sending ${messageCount * serversCount} messages in ${endTime - startTime} milliseconds")
            servers.forEach { it.close() }
        }
    }

    @Nested
    inner class DualStreamTests {
        @Test
        @Timeout(60)
        fun `gateway to gateway - dual stream`() {
            val aliceGatewayAddress = URI.create("https://www.chip.net:${getOpenPort()}")
            val bobGatewayAddress = URI.create("https://www.dale.net:${getOpenPort()}")
            val messageCount = 100
            alice.publish(
                Record(GATEWAY_TLS_TRUSTSTORES, "$aliceX500name-$GROUP_ID",
                    GatewayTruststore(HoldingIdentity(aliceX500name, GROUP_ID), listOf(truststoreCertificatePem))))
            bob.publish(
                Record(GATEWAY_TLS_TRUSTSTORES, "$bobX500Name-$GROUP_ID",
                    GatewayTruststore(HoldingIdentity(bobX500Name, GROUP_ID), listOf(truststoreCertificatePem))))
            alice.publishKeyStoreCertificatesAndKeys(chipKeyStore, aliceHoldingIdentity)
            bob.publishKeyStoreCertificatesAndKeys(daleKeyStore, bobHoldingIdentity)

            val startTime = Instant.now().toEpochMilli()
            // Start the gateways and let them run until all messages have been processed
            val gateways = listOf(
                Gateway(
                    createConfigurationServiceFor(
                        GatewayConfiguration(
                            listOf(
                                GatewayServerConfiguration(
                                    aliceGatewayAddress.host,
                                    aliceGatewayAddress.port,
                                    "/",
                                )
                            ),
                            aliceSslConfig,
                            MAX_REQUEST_SIZE
                        ),
                        alice.lifecycleCoordinatorFactory
                    ),
                    alice.subscriptionFactory,
                    alice.publisherFactory,
                    alice.lifecycleCoordinatorFactory,
                    alice.cryptoOpsClient,
                    avroSchemaRegistry,
                    platformInfoProvider,
                    bootConfig,
                    messagingConfig(),
                ),
                Gateway(
                    createConfigurationServiceFor(
                        GatewayConfiguration(
                            listOf(
                                GatewayServerConfiguration(
                                    bobGatewayAddress.host,
                                    bobGatewayAddress.port,
                                    "/",

                                )
                            ),
                            bobSslConfig,
                            MAX_REQUEST_SIZE
                        ),
                        bob.lifecycleCoordinatorFactory
                    ),
                    bob.subscriptionFactory,
                    bob.publisherFactory,
                    bob.lifecycleCoordinatorFactory,
                    bob.cryptoOpsClient,
                    avroSchemaRegistry,
                    platformInfoProvider,
                    bootConfig,
                    messagingConfig(),
                )
            ).onEach {
                it.startAndWaitForStarted()
            }

            val messagesFromAlice = (1..messageCount).map {
                val msg = LinkOutMessage.newBuilder().apply {
                    header = LinkOutHeader(
                        bobHoldingIdentity,
                        aliceHoldingIdentity,
                        NetworkType.CORDA_5,
                        bobGatewayAddress.toString()
                    )
                    payload = authenticatedP2PMessage("Target-$bobGatewayAddress")
                }.build()
                alice.publish(Record(LINK_OUT_TOPIC, "key", msg))
            }
            val messagesFromBob = (1..messageCount).map {
                val msg = LinkOutMessage.newBuilder().apply {
                    header = LinkOutHeader(
                        aliceHoldingIdentity,
                        bobHoldingIdentity,
                        NetworkType.CORDA_5,
                        aliceGatewayAddress.toString()
                    )
                    payload = authenticatedP2PMessage("Target-$aliceGatewayAddress")
                }.build()
                bob.publish(Record(LINK_OUT_TOPIC, "key", msg))
            }
            (messagesFromAlice + messagesFromBob).flatten().forEach { it.join() }

            val aliceReceivedMessages = alice.getLinkInMessages(messageCount)
            assertThat(aliceReceivedMessages)
                .hasSize(messageCount)
            val bobReceivedMessages = bob.getLinkInMessages(messageCount)
            assertThat(bobReceivedMessages)
                .hasSize(messageCount)
            val endTime = Instant.now().toEpochMilli()
            logger.info("Done processing ${messageCount * 2} in ${endTime - startTime} milliseconds.")
            gateways.map {
                thread {
                    it.stop()
                }
            }.forEach {
                it.join()
            }
        }
    }

    private fun authenticatedP2PMessage(content: String) = AuthenticatedDataMessage.newBuilder().apply {
        header = CommonHeader(MessageType.DATA, 0, sessionId, 1L, Instant.now().toEpochMilli())
        payload = ByteBuffer.wrap(content.toByteArray())
        authTag = ByteBuffer.wrap(ByteArray(0))
    }.build()

    @Nested
    inner class BadConfigurationTests {
        @Test
        @Timeout(120)
        fun `Gateway can recover from bad configuration`() {
            val configPublisher = ConfigPublisher()
            keep(configPublisher)
            val host = "www.alice.net"
            Gateway(
                configPublisher.readerService,
                alice.subscriptionFactory,
                alice.publisherFactory,
                alice.lifecycleCoordinatorFactory,
                alice.cryptoOpsClient,
                avroSchemaRegistry,
                platformInfoProvider,
                bootConfig,
                messagingConfig(),
            ).usingLifecycle { gateway ->
                val port = getOpenPort()
                logger.info("Publishing good config")
                configPublisher.publishConfig(
                    GatewayConfiguration(
                        listOf(
                            GatewayServerConfiguration(
                                host,
                                port,
                                "/",
                            )
                        ),
                        aliceSslConfig,
                        MAX_REQUEST_SIZE
                    )
                )
                gateway.startAndWaitForStarted()
                assertThat(gateway.dominoTile.status).isEqualTo(LifecycleStatus.UP)

                logger.info("Publishing bad config")
                // -20 is invalid port, serer should fail
                configPublisher.publishConfig(
                    GatewayConfiguration(
                        listOf(
                            GatewayServerConfiguration(
                                host,
                                -20,
                                "/",
                            )
                        ),
                        aliceSslConfig,
                        MAX_REQUEST_SIZE
                    )
                )
                eventually(duration = 20.seconds) {
                    assertThat(gateway.dominoTile.status).isEqualTo(LifecycleStatus.DOWN)
                }
                eventually(duration = 20.seconds) {
                    assertThrows<ConnectException> {
                        Socket(host, port).close()
                    }
                }

                logger.info("Publishing good config again")
                val anotherPort = getOpenPort()
                configPublisher.publishConfig(
                    GatewayConfiguration(
                        listOf(
                            GatewayServerConfiguration(
                                host,
                                anotherPort,
                                "/",
                            )
                        ),
                        aliceSslConfig,
                        MAX_REQUEST_SIZE
                    )
                )
                eventually(duration = 20.seconds) {
                    assertThat(gateway.dominoTile.status).isEqualTo(LifecycleStatus.UP)
                }
                assertDoesNotThrow {
                    Socket(host, anotherPort).close()
                }

                logger.info("Publishing bad config again")
                configPublisher.publishBadConfig()
                eventually(duration = 20.seconds) {
                    assertThat(gateway.dominoTile.status).isEqualTo(LifecycleStatus.DOWN)
                }
                eventually(duration = 20.seconds) {
                    assertThrows<ConnectException> {
                        Socket(host, anotherPort).close()
                    }
                }
            }
        }
    }

    @Nested
    inner class DynamicKeyStore {
        private fun testClientWith(
            server: URI,
            trustStorePem: String,
        ) {
            val truststore = TrustStoresMap.TrustedCertificates(listOf(trustStorePem))
            val serverInfo = DestinationInfo(
                server, server.host, null,
                truststore, null
            )
            val linkInMessage = LinkInMessage(authenticatedP2PMessage(""))
            val gatewayMessage = GatewayMessage(UUID.randomUUID().toString(), linkInMessage.payload)
            HttpClient(
                serverInfo,
                SslConfiguration(RevocationConfig(RevocationConfigMode.OFF), TlsType.ONE_WAY),
                NioEventLoopGroup(1),
                NioEventLoopGroup(1),
                ConnectionConfiguration(),
            ).use { client ->
                client.start()
                val httpResponse = client.write(avroSchemaRegistry.serialize(gatewayMessage).array()).getOrThrow()
                assertThat(httpResponse.statusCode).isEqualTo(HttpResponseStatus.OK)
                assertThat(httpResponse.payload).isNotNull
                val gatewayResponse = avroSchemaRegistry.deserialize<GatewayResponse>(ByteBuffer.wrap(httpResponse.payload))
                assertThat(gatewayResponse.id).isEqualTo(gatewayMessage.id)
            }
        }

        private fun CertificateAuthority.toGatewayTrustStore(sourceX500Name: String): GatewayTruststore {
            return GatewayTruststore(
                HoldingIdentity(sourceX500Name, GROUP_ID),
                listOf(
                    this.caCertificate.toPem()
                )
            )
        }

        fun PrivateKeyWithCertificate.toKeyStoreAndPassword(): KeyStoreWithPassword {
            return KeyStoreWithPassword(
                this.toKeyStore(),
                CertificateAuthority.PASSWORD
            )
        }

        @Test
        @Timeout(120)
        fun `key store can change dynamically`() {
            val aliceAddress = URI.create("https://www.alice.net:${getOpenPort()}")
            val bobAddress = URI.create("https://www.bob.net:${getOpenPort()}")
            val server = Node("server")
            val configPublisher = ConfigPublisher()
            keep(configPublisher)
            configPublisher.publishConfig(
                GatewayConfiguration(
                    listOf(
                        GatewayServerConfiguration(
                            aliceAddress.host,
                            aliceAddress.port,
                            "/",
                        )
                    ),
                    aliceSslConfig,
                    MAX_REQUEST_SIZE
                ),
            )
            Gateway(
                configPublisher.readerService,
                server.subscriptionFactory,
                server.publisherFactory,
                server.lifecycleCoordinatorFactory,
                server.cryptoOpsClient,
                avroSchemaRegistry,
                platformInfoProvider,
                bootConfig,
                messagingConfig(),
            ).usingLifecycle { gateway ->
                gateway.startAndWaitForStarted()
                val firstCertificatesAuthority = CertificateAuthorityFactory
                    .createMemoryAuthority(RSA_TEMPLATE.toFactoryDefinitions())
                // Client should fail without trust store certificates
                assertThrows<RuntimeException> {
                    testClientWith(aliceAddress, firstCertificatesAuthority.caCertificate.toPem())
                }

                // Publish the trust store
                server.publish(
                    Record(GATEWAY_TLS_TRUSTSTORES, "$aliceX500name-$GROUP_ID",
                        firstCertificatesAuthority.toGatewayTrustStore(aliceX500name)),
                )

                // Client should fail without any keys
                assertThrows<RuntimeException> {
                    testClientWith(aliceAddress, firstCertificatesAuthority.caCertificate.toPem())
                }

                // Publish the first key pair
                val keyStore =
                    firstCertificatesAuthority.generateKeyAndCertificate(aliceAddress.host).toKeyStoreAndPassword()
                server.publishKeyStoreCertificatesAndKeys(keyStore, aliceHoldingIdentity)

                // Client should now pass
                eventually {
                    testClientWith(aliceAddress, firstCertificatesAuthority.caCertificate.toPem())
                }

                // Delete the first pair
                server.cryptoOpsClient.removeTenantKeys()


                // Client should fail again...
                eventually {
                    assertThrows<RuntimeException> {
                        testClientWith(aliceAddress, firstCertificatesAuthority.caCertificate.toPem())
                    }
                }

                // publish the same pair again
                server.publishKeyStoreCertificatesAndKeys(keyStore, aliceHoldingIdentity)

                // Client should now pass
                eventually {
                    testClientWith(aliceAddress, firstCertificatesAuthority.caCertificate.toPem())
                }

                // Delete the certificates
                val subject = PrincipalUtil.getSubjectX509Principal(
                    keyStore.keyStore.getCertificate(keyStore.keyStore.aliases().nextElement()) as X509Certificate
                ).name
                server.publish(
                    Record(
                        Schemas.P2P.GATEWAY_TLS_CERTIFICATES,
                        subject,
                        null
                    )
                )
                eventually {
                    assertThrows<RuntimeException> {
                        testClientWith(aliceAddress, firstCertificatesAuthority.caCertificate.toPem())
                    }
                }

                // publish it again
                server.publishKeyStoreCertificatesAndKeys(keyStore, aliceHoldingIdentity)
                eventually {
                    testClientWith(aliceAddress, firstCertificatesAuthority.caCertificate.toPem())
                }

                // Change the host
                configPublisher.publishConfig(
                    GatewayConfiguration(
                        listOf(GatewayServerConfiguration(bobAddress.host, bobAddress.port, "/")),
                        aliceSslConfig, MAX_REQUEST_SIZE)
                )
                val bobKeyStore =
                    firstCertificatesAuthority.generateKeyAndCertificate(bobAddress.host).toKeyStoreAndPassword()
                server.publishKeyStoreCertificatesAndKeys(bobKeyStore, aliceHoldingIdentity)

                // Client should pass with new host
                eventually {
                    testClientWith(bobAddress, firstCertificatesAuthority.caCertificate.toPem())
                }

                // new trust store...
                val secondCertificatesAuthority = CertificateAuthorityFactory
                    .createMemoryAuthority(ECDSA_SECP256R1_TEMPLATE.toFactoryDefinitions())
                server.publish(
                    Record(GATEWAY_TLS_TRUSTSTORES, "$aliceX500name-$GROUP_ID",
                        secondCertificatesAuthority.toGatewayTrustStore(aliceX500name)),
                )

                // replace the first pair
                val newKeyStore =
                    secondCertificatesAuthority.generateKeyAndCertificate(bobAddress.host).toKeyStoreAndPassword()
                server.publishKeyStoreCertificatesAndKeys(newKeyStore, aliceHoldingIdentity)

                eventually {
                    testClientWith(bobAddress, secondCertificatesAuthority.caCertificate.toPem())
                }

                // verify that the old trust store will fail
                eventually {
                    assertThrows<RuntimeException> {
                        testClientWith(bobAddress, firstCertificatesAuthority.caCertificate.toPem())
                    }
                }

                // new trust store and pair...
                val thirdCertificatesAuthority = CertificateAuthorityFactory
                    .createMemoryAuthority(ECDSA_SECP256R1_TEMPLATE.toFactoryDefinitions())
                server.publish(
                    Record(
                        GATEWAY_TLS_TRUSTSTORES,
                        "$aliceX500name-$GROUP_ID",
                        thirdCertificatesAuthority.toGatewayTrustStore(aliceX500name)),
                )

                // publish new pair with new alias
                val newerKeyStore =
                    thirdCertificatesAuthority.generateKeyAndCertificate(bobAddress.host).toKeyStoreAndPassword()
                server.publishKeyStoreCertificatesAndKeys(newerKeyStore, aliceHoldingIdentity)

                eventually {
                    testClientWith(bobAddress, thirdCertificatesAuthority.caCertificate.toPem())
                }
            }
        }

        @Test
        @Timeout(120)
        fun `have more than one CA`() {
            val aliceAddress = URI.create("https://www.alice.net:${getOpenPort()}")
            val size = 5
            val holdingIdToCa = (1..size).map {
                HoldingIdentity("CN=Alice-$it, O=Alice Corp, L=LDN, C=GB", GROUP_ID) to
                CertificateAuthorityFactory
                    .createMemoryAuthority(
                        RSA_TEMPLATE.toFactoryDefinitions(),
                    )
            }.toMap()
            val configPublisher = ConfigPublisher().also {
                keep(it)
            }
            configPublisher.publishConfig(
                GatewayConfiguration(
                    listOf(
                        GatewayServerConfiguration(
                            aliceAddress.host,
                            aliceAddress.port,
                            "/",
                        )
                    ),
                    aliceSslConfig,
                    MAX_REQUEST_SIZE
                ),
            )
            val server = Node("server")

            Gateway(
                configPublisher.readerService,
                server.subscriptionFactory,
                server.publisherFactory,
                server.lifecycleCoordinatorFactory,
                server.cryptoOpsClient,
                avroSchemaRegistry,
                platformInfoProvider,
                bootConfig,
                messagingConfig(),
            ).usingLifecycle { gateway ->
                gateway.startAndWaitForStarted()

                // Publish the trust stores and key stores
                holdingIdToCa.forEach { holdingId, ca ->
                    val name = holdingId.x500Name
                    server.publish(
                        Record(GATEWAY_TLS_TRUSTSTORES, "$name-$GROUP_ID",
                            ca.toGatewayTrustStore(name)),
                    )
                    val keyStore =
                        ca.generateKeyAndCertificate(aliceAddress.host).toKeyStoreAndPassword()
                    server.publishKeyStoreCertificatesAndKeys(keyStore, holdingId, name)
                }

                eventually {
                    holdingIdToCa.values.forEach { ca ->
                        testClientWith(aliceAddress, ca.caCertificate.toPem())
                    }
                }

            }
        }

    }

    @Nested
    inner class MutualTls {
        @Test
        @Timeout(60)
        fun `gateway to gateway - mutual TLS`() {
            val aliceGatewayAddress = URI.create("https://127.0.0.1:${getOpenPort()}")
            val bobGatewayAddress = URI.create("https://www.chip.net:${getOpenPort()}")
            val messageCount = 100
            alice.publish(
                Record(GATEWAY_TLS_TRUSTSTORES, "$aliceX500name-$GROUP_ID",
                    GatewayTruststore(HoldingIdentity(aliceX500name, GROUP_ID), listOf(truststoreCertificatePem))))
            bob.publish(
                Record(GATEWAY_TLS_TRUSTSTORES, "$bobX500Name-$GROUP_ID",
                    GatewayTruststore(HoldingIdentity(bobX500Name, GROUP_ID), listOf(truststoreCertificatePem))))
            alice.publishKeyStoreCertificatesAndKeys(ipKeyStore, aliceHoldingIdentity)
            bob.publishKeyStoreCertificatesAndKeys(chipKeyStore, bobHoldingIdentity)
            bob.allowCertificates(ipKeyStore)
            alice.allowCertificates(chipKeyStore)
            alice.listenToLinkManagerRpc()
            bob.listenToLinkManagerRpc()

            val startTime = Instant.now().toEpochMilli()
            // Start the gateways and let them run until all messages have been processed
            val gateways = listOf(
                Gateway(
                    createConfigurationServiceFor(
                        GatewayConfiguration(
                            listOf(
                                GatewayServerConfiguration(
                                    aliceGatewayAddress.host,
                                    aliceGatewayAddress.port,
                                    "/",
                                )
                            ),
                            aliceSslConfig.copy(tlsType = TlsType.MUTUAL),
                            MAX_REQUEST_SIZE
                        ),
                        alice.lifecycleCoordinatorFactory
                    ),
                    alice.subscriptionFactory,
                    alice.publisherFactory,
                    alice.lifecycleCoordinatorFactory,
                    alice.cryptoOpsClient,
                    avroSchemaRegistry,
                    platformInfoProvider,
                    bootConfig,
                    messagingConfig(),
                ),
                Gateway(
                    createConfigurationServiceFor(
                        GatewayConfiguration(
                            listOf(
                                GatewayServerConfiguration(
                                    bobGatewayAddress.host,
                                    bobGatewayAddress.port,
                                    "/",
                                )
                            ),
                            bobSslConfig.copy(tlsType = TlsType.MUTUAL),
                            MAX_REQUEST_SIZE
                        ),
                        bob.lifecycleCoordinatorFactory
                    ),
                    bob.subscriptionFactory,
                    bob.publisherFactory,
                    bob.lifecycleCoordinatorFactory,
                    bob.cryptoOpsClient,
                    avroSchemaRegistry,
                    platformInfoProvider,
                    bootConfig,
                    messagingConfig(),
                )
            ).onEach {
                it.startAndWaitForStarted()
            }

            val messagesFromAlice = (1..messageCount).map {
                val msg = LinkOutMessage.newBuilder().apply {
                    header = LinkOutHeader(
                        bobHoldingIdentity,
                        aliceHoldingIdentity,
                        NetworkType.CORDA_5,
                        bobGatewayAddress.toString()
                    )
                    payload = authenticatedP2PMessage("Target-$bobGatewayAddress")
                }.build()
                alice.publish(Record(LINK_OUT_TOPIC, "key", msg))
            }
            val messagesFromBob = (1..messageCount).map {
                val msg = LinkOutMessage.newBuilder().apply {
                    header = LinkOutHeader(
                        aliceHoldingIdentity,
                        bobHoldingIdentity,
                        NetworkType.CORDA_5,
                        aliceGatewayAddress.toString()
                    )
                    payload = authenticatedP2PMessage("Target-$aliceGatewayAddress")
                }.build()
                bob.publish(Record(LINK_OUT_TOPIC, "key", msg))
            }
            (messagesFromAlice + messagesFromBob).flatten().forEach { it.join() }

            val aliceReceivedMessages = alice.getLinkInMessages(messageCount)
            assertThat(aliceReceivedMessages).hasSize(messageCount)
            val bobReceivedMessages = bob.getLinkInMessages(messageCount)
            assertThat(bobReceivedMessages).hasSize(messageCount)

            val endTime = Instant.now().toEpochMilli()
            logger.info("Done processing ${messageCount * 2} in ${endTime - startTime} milliseconds.")

            gateways.map {
                thread {
                    it.stop()
                }
            }.forEach {
                it.join()
            }
        }
    }
}

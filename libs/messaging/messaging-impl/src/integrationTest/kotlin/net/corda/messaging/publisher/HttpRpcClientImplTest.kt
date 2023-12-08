package net.corda.messaging.publisher

import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.GatewayTlsCertificates
import net.corda.data.p2p.GatewayTruststore
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.LifecycleCoordinatorSchedulerFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.messagebus.kafka.serialization.CordaAvroDeserializerImpl
import net.corda.messagebus.kafka.serialization.CordaAvroSerializerImpl
import net.corda.messaging.api.processor.SyncRPCProcessor
import net.corda.messaging.api.publisher.send
import net.corda.messaging.api.subscription.config.SyncRPCConfig
import net.corda.messaging.subscription.SyncRPCSubscriptionImpl
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl
import net.corda.web.server.JavalinServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.net.ServerSocket
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class HttpRpcClientImplTest {
    @Test
    fun `client sends the correct request to the server`() {
        val config = SyncRPCConfig(
            name = "test",
            endpoint = "/endpoint",
        )
        val requests = ConcurrentHashMap.newKeySet<GatewayTlsCertificates>()
        val responses = listOf(
            GatewayTruststore(
                HoldingIdentity(
                    "member-1",
                    "group-1",
                ),
                listOf("one", "two"),
            ),
            GatewayTruststore(
                HoldingIdentity(
                    "member-2",
                    "group-2",
                ),
                listOf("three"),
            )
        )
        val processor = object : SyncRPCProcessor<GatewayTlsCertificates, GatewayTruststore> {
            private val index = AtomicInteger()
            override fun process(request: GatewayTlsCertificates): GatewayTruststore? {
                requests.add(request)
                return responses.getOrNull(index.getAndIncrement())
            }

            override val requestClass = GatewayTlsCertificates::class.java
            override val responseClass = GatewayTruststore::class.java
        }
        val lifecycleCoordinatorFactory =
            LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl(), LifecycleCoordinatorSchedulerFactoryImpl())
        val version = "5.x"
        val platformInfo = mock<PlatformInfoProvider> {
            on { localWorkerSoftwareShortVersion } doReturn version
        }
        val webServer = JavalinServer(
            lifecycleCoordinatorFactory,
            platformInfo,
        )
        val port = ServerSocket(0).use {
            it.localPort
        }
        webServer.start(port)
        val schemaRegistry = AvroSchemaRegistryImpl()
        SyncRPCSubscriptionImpl(
            config,
            processor,
            lifecycleCoordinatorFactory,
            webServer,
            CordaAvroSerializerImpl(schemaRegistry) { _ -> },
            CordaAvroDeserializerImpl(schemaRegistry, {_ -> }, GatewayTlsCertificates::class.java),
        ).use { subscription ->
            subscription.start()

            val client = HttpRpcClientImpl(
                avroSchemaRegistry = schemaRegistry,
            )
            val requestsToSend = listOf(
                GatewayTlsCertificates(
                    "test-1",
                    HoldingIdentity(
                        "m1",
                        "g1",
                    ),
                    listOf("test1",)
                ),
                GatewayTlsCertificates(
                    "test-2",
                    HoldingIdentity(
                        "m2",
                        "g2",
                    ),
                    listOf("test2",)
                ),
                GatewayTlsCertificates(
                    "test-3",
                    HoldingIdentity(
                        "m3",
                        "g3",
                    ),
                    listOf("test3",)
                ),
            )
            val uri = URI.create("http://127.0.0.1:$port/api/$version${config.endpoint}")
            val replies = requestsToSend.map {
                client.send<GatewayTruststore>(uri, it)
            }

            assertThat(requests).containsExactlyInAnyOrderElementsOf(requestsToSend)
            assertThat(replies).hasSize(requestsToSend.size)
            assertThat(replies.filterNotNull()).containsExactlyInAnyOrderElementsOf(responses)
        }
        webServer.stop()
    }
}
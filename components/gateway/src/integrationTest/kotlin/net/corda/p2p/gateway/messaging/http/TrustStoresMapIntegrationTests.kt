package net.corda.p2p.gateway.messaging.http

import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.publisher.factory.CordaPublisherFactory
import net.corda.messaging.emulation.rpc.RPCTopicServiceImpl
import net.corda.messaging.emulation.subscription.factory.InMemSubscriptionFactory
import net.corda.messaging.emulation.topic.service.impl.TopicServiceImpl
import net.corda.p2p.GatewayTruststore
import net.corda.p2p.gateway.TestBase
import net.corda.schema.Schemas
import net.corda.test.util.eventually
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.StringWriter

class TrustStoresMapIntegrationTests : TestBase() {
    companion object {
        private const val GROUP_ID = "Group-A"
        private const val instanceId = 32
    }
    private val topicService = TopicServiceImpl()
    private val rpcTopicService = RPCTopicServiceImpl()
    private val subscriptionFactory = InMemSubscriptionFactory(topicService, rpcTopicService, lifecycleCoordinatorFactory)
    private val messagingConfig = SmartConfigImpl.empty()
    private val publisherFactory = CordaPublisherFactory(topicService, rpcTopicService, lifecycleCoordinatorFactory)
    private val expectedCertificatePem = truststoreCertificatePem.replace("\r\n", "\n")

    @Test
    fun `TrustStoresMap creates correct certificate`() {
        val map = TrustStoresMap(
            lifecycleCoordinatorFactory,
            subscriptionFactory,
            messagingConfig,
            instanceId,
        )
        publisherFactory.createPublisher(PublisherConfig("client.ID")).use {
            it.publish(
                listOf(
                    Record(
                        Schemas.P2P.GATEWAY_TLS_TRUSTSTORES,
                        GROUP_ID,
                        GatewayTruststore(listOf(expectedCertificatePem))
                    )
                )
            ).forEach {
                it.join()
            }
        }

        map.start()

        eventually {
            assertThat(map.isRunning).isTrue

            val store = assertDoesNotThrow {
                map.getTrustStore(GROUP_ID)
            }

            val certificate = store.aliases().toList().map {
                store.getCertificate(it)
            }.single()

            val pemCertificate = StringWriter().use { str ->
                JcaPEMWriter(str).use { writer ->
                    writer.writeObject(certificate)
                }
                str.toString()
            }.replace("\r\n", "\n")

            assertThat(pemCertificate).isEqualTo(expectedCertificatePem)
        }
    }
}

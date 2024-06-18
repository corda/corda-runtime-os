package net.corda.p2p.gateway.messaging.http

import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.publisher.factory.CordaPublisherFactory
import net.corda.messaging.emulation.rpc.RPCTopicServiceImpl
import net.corda.messaging.emulation.subscription.factory.InMemSubscriptionFactory
import net.corda.messaging.emulation.topic.service.impl.TopicServiceImpl
import net.corda.data.p2p.GatewayTruststore
import net.corda.p2p.gateway.TestBase
import net.corda.schema.Schemas
import net.corda.test.util.eventually
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.StringWriter
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import net.corda.p2p.gateway.messaging.GatewayServerConfiguration

internal class TrustStoresMapIntegrationTests : TestBase() {
    private companion object {
        const val GROUP_ID = "Group-A"
        const val ALICE_NAME = "O=Alice, L=LDN, C=GB"
        const val MAX_REQUEST_SIZE = 50_000_000L
    }
    private val topicService = TopicServiceImpl().also {
        keep(it)
    }
    private val rpcTopicService = RPCTopicServiceImpl()
    private val subscriptionFactory = InMemSubscriptionFactory(topicService, rpcTopicService, lifecycleCoordinatorFactory)
    private val messagingConfig = SmartConfigImpl.empty()
    private val publisherFactory = CordaPublisherFactory(topicService, rpcTopicService, lifecycleCoordinatorFactory)
    private val expectedCertificatePem = truststoreCertificatePem.replace("\r\n", "\n")
    private val configPublisher = ConfigPublisher()

    @Test
    fun `TrustStoresMap creates correct certificate`() {
        val map = TrustStoresMap(
            lifecycleCoordinatorFactory,
            subscriptionFactory,
            messagingConfig,
            configPublisher.readerService
        )
        publisherFactory.createPublisher(PublisherConfig("client.ID", false), messagingConfig).use {
            it.publish(
                listOf(
                    Record(
                        Schemas.P2P.GATEWAY_TLS_TRUSTSTORES,
                        "$ALICE_NAME-$GROUP_ID",
                        GatewayTruststore(HoldingIdentity(ALICE_NAME, GROUP_ID), listOf(expectedCertificatePem))
                    )
                )
            ).forEach {
                it.join()
            }
        }
        configPublisher.publishConfig(
            GatewayConfiguration(
            listOf(GatewayServerConfiguration("25", 25, "/",)),
            aliceSslConfig,
            MAX_REQUEST_SIZE,
        ),)

        map.start()
        eventually {
            assertThat(map.isRunning).isTrue

            val store = assertDoesNotThrow {
                map.getTrustStore(MemberX500Name.parse(ALICE_NAME), GROUP_ID).trustStore
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

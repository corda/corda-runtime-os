package net.corda.p2p.gateway.certificates

import net.corda.data.p2p.gateway.certificates.RevocationCheckRequest
import net.corda.data.p2p.gateway.certificates.RevocationCheckStatus
import net.corda.data.p2p.gateway.certificates.RevocationMode
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.LifecycleCoordinatorSchedulerFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.messaging.emulation.publisher.factory.CordaPublisherFactory
import net.corda.messaging.emulation.rpc.RPCTopicServiceImpl
import net.corda.messaging.emulation.subscription.factory.InMemSubscriptionFactory
import net.corda.messaging.emulation.topic.service.impl.TopicServiceImpl
import net.corda.testing.p2p.certificates.Certificates
import net.corda.utilities.concurrent.getOrThrow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

class RevocationCheckerTest {
    private companion object {
        val futureTimeOut: Duration = Duration.ofSeconds(60)
        val lifecycleCoordinatorFactory = LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl(), LifecycleCoordinatorSchedulerFactoryImpl())
        val topicService = TopicServiceImpl()
        val rpcTopicService = RPCTopicServiceImpl()
        val subscriptionFactory = InMemSubscriptionFactory(topicService, rpcTopicService, lifecycleCoordinatorFactory)
        val messagingConfig = SmartConfigImpl.empty()
        val publisherFactory = CordaPublisherFactory(topicService, rpcTopicService, lifecycleCoordinatorFactory)
        val revocationChecker = RevocationChecker(subscriptionFactory, messagingConfig, lifecycleCoordinatorFactory)
        val sender = publisherFactory.createRPCSender(revocationChecker.subscriptionConfig, messagingConfig)

        @BeforeAll
        @JvmStatic
        fun setup() {
            revocationChecker.start()
            sender.start()
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            revocationChecker.close()
            sender.close()
        }
    }

    private val aliceCert = Certificates.aliceKeyStorePem.readText()
    private val corruptedAliceCert = Certificates.aliceKeyStorePem.readText().slice(0..aliceCert.length - 10)
    private val revokedBobCert = Certificates.bobKeyStorePem.readText()
    private val trustStore = listOf(Certificates.truststoreCertificatePem.readText())
    private val wrongTrustStore = listOf(Certificates.c4TruststoreCertificatePem.readText())

    @Test
    fun `valid certificate passes validation`() {
        val resultFuture = sender.sendRequest(RevocationCheckRequest(listOf(aliceCert), trustStore, RevocationMode.HARD_FAIL))
        val result = resultFuture.getOrThrow(futureTimeOut)
        assertThat(result).isEqualTo(RevocationCheckStatus.ACTIVE)
    }

    @Test
    fun `corrupeted certificate causes the future to complete exceptionally`() {
        val resultFuture = sender.sendRequest(RevocationCheckRequest(listOf(corruptedAliceCert), trustStore, RevocationMode.HARD_FAIL))
        assertThrows<ExecutionException> { resultFuture.get(5, TimeUnit.SECONDS) }
    }

    @Test
    fun `revoked certificate fails validation with HARD FAIL mode`() {
        val resultFuture = sender.sendRequest(RevocationCheckRequest(listOf(revokedBobCert), trustStore, RevocationMode.HARD_FAIL))
        val result = resultFuture.getOrThrow(futureTimeOut)
        assertThat(result).isEqualTo(RevocationCheckStatus.REVOKED)
    }

    @Test
    fun `revoked certificate fails validation with SOFT FAIL mode`() {
        val resultFuture = sender.sendRequest(RevocationCheckRequest(listOf(revokedBobCert), trustStore, RevocationMode.SOFT_FAIL))
        val result = resultFuture.getOrThrow(futureTimeOut)
        assertThat(result).isEqualTo(RevocationCheckStatus.REVOKED)
    }

    @Test
    fun `if truststore is wrong validation fails`() {
        val resultFuture = sender.sendRequest(RevocationCheckRequest(listOf(aliceCert), wrongTrustStore, RevocationMode.HARD_FAIL))
        val result = resultFuture.getOrThrow(futureTimeOut)
        assertThat(result).isEqualTo(RevocationCheckStatus.REVOKED)
    }
}
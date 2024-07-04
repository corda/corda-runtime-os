package net.corda.p2p.gateway.certificates

import net.corda.crypto.cipher.suite.schemes.RSA_TEMPLATE
import net.corda.crypto.test.certificates.generation.CertificateAuthorityFactory
import net.corda.crypto.test.certificates.generation.toFactoryDefinitions
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.data.p2p.gateway.certificates.Active
import net.corda.data.p2p.gateway.certificates.RevocationCheckRequest
import net.corda.data.p2p.gateway.certificates.RevocationCheckResponse
import net.corda.data.p2p.gateway.certificates.RevocationMode
import net.corda.data.p2p.gateway.certificates.Revoked
import net.corda.lifecycle.domino.logic.util.RPCSubscriptionDominoTile
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.testing.p2p.certificates.Certificates
import net.corda.utilities.concurrent.getOrThrow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.MockedConstruction
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

class RevocationCheckerTest {

    private val subscription = mock<RPCSubscription<RevocationCheckRequest, RevocationCheckResponse>>()
    private val processor = argumentCaptor<RPCResponderProcessor<RevocationCheckRequest, RevocationCheckResponse>>()
    private val subscriptionFactory = mock<SubscriptionFactory> {
        on {
            createRPCSubscription(
                any(),
                any(),
                processor.capture(),
            )
        } doReturn subscription
    }
    private var mockDominoTile: MockedConstruction<RPCSubscriptionDominoTile<*, *>>? = null
    private var revocationChecker: RevocationChecker? = null

    private val ca = CertificateAuthorityFactory.createRevocableAuthority(
        RSA_TEMPLATE.toFactoryDefinitions(),
    )
    private val trustStoreWithRevocation = listOf(
        ca.caCertificate.toPem(),
    )
    private val revokedCert = ca.generateKeyAndCertificates("www.bob.net").certificates.also {
        ca.revoke(it.first())
    }
    private val cert = ca.generateKeyAndCertificates("www.bob.net").certificates.toPem()
    private val corruptedCert =
        cert.dropLast(20)
    private val wrongTrustStore = listOf(Certificates.c4TruststoreCertificatePem.readText())

    @BeforeEach
    fun setup() {
        mockDominoTile = Mockito.mockConstruction(RPCSubscriptionDominoTile::class.java) { _, context ->
            @Suppress("UNCHECKED_CAST")
            (context.arguments()[1] as () -> RPCSubscription<RevocationCheckRequest, RevocationCheckResponse>)()
        }
        revocationChecker = RevocationChecker(subscriptionFactory, mock(), mock(), mock())
    }

    @AfterEach
    fun tearDown() {
        mockDominoTile?.close()
        ca.close()
    }

    @Test
    fun `valid certificate passes validation`() {
        val result = CompletableFuture<RevocationCheckResponse>()
        processor.firstValue.onNext(
            RevocationCheckRequest(listOf(cert), trustStoreWithRevocation, RevocationMode.HARD_FAIL),
            result,
        )
        assertThat(result.getOrThrow().status).isEqualTo(Active())
    }

    @Test
    fun `corrupeted certificate causes the future to complete exceptionally`() {
        val result = CompletableFuture<RevocationCheckResponse>()
        processor.firstValue.onNext(
            RevocationCheckRequest(listOf(corruptedCert), trustStoreWithRevocation, RevocationMode.HARD_FAIL),
            result,
        )
        assertThrows<ExecutionException> { result.get() }
    }

    @Test
    fun `revoked certificate fails validation with HARD FAIL mode`() {
        val result = CompletableFuture<RevocationCheckResponse>()
        processor.firstValue.onNext(
            RevocationCheckRequest(listOf(revokedCert.toPem()), trustStoreWithRevocation, RevocationMode.HARD_FAIL),
            result,
        )
        assertThat(result.getOrThrow().status).isInstanceOf(Revoked::class.java)
    }

    @Test
    fun `revoked certificate fails validation with SOFT FAIL mode`() {
        val resultFuture = CompletableFuture<RevocationCheckResponse>()
        processor.firstValue.onNext(
            RevocationCheckRequest(listOf(revokedCert.toPem()), trustStoreWithRevocation, RevocationMode.SOFT_FAIL),
            resultFuture,
        )
        assertThat(resultFuture.getOrThrow().status).isInstanceOf(Revoked::class.java)
    }

    @Test
    fun `if truststore is wrong validation fails`() {
        val resultFuture = CompletableFuture<RevocationCheckResponse>()
        processor.firstValue.onNext(
            RevocationCheckRequest(listOf(cert), wrongTrustStore, RevocationMode.HARD_FAIL),
            resultFuture,
        )
        assertThat(resultFuture.getOrThrow().status).isInstanceOf(Revoked::class.java)
    }
}

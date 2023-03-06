package net.corda.p2p.gateway.certificates

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
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
    private val mockDominoTile = Mockito.mockConstruction(RPCSubscriptionDominoTile::class.java) { _, context ->
        @Suppress("UNCHECKED_CAST")
        (context.arguments()[1] as  () -> RPCSubscription<RevocationCheckRequest, RevocationCheckResponse>)()
    }
    init {
        RevocationChecker(subscriptionFactory, mock(), mock())
    }

    @AfterEach
    fun tearDown() {
        mockDominoTile.close()
    }

    private val aliceCert = Certificates.aliceKeyStorePem.readText()
    private val corruptedAliceCert = Certificates.aliceKeyStorePem.readText().slice(0..aliceCert.length - 10)
    private val revokedBobCert = Certificates.bobKeyStorePem.readText()
    private val trustStore = listOf(Certificates.truststoreCertificatePem.readText())
    private val wrongTrustStore = listOf(Certificates.c4TruststoreCertificatePem.readText())

    @Test
    @Disabled("Disabling temporarily until CORE-11411 is completed.")
    fun `valid certificate passes validation`() {
        val result = CompletableFuture<RevocationCheckResponse>()
        processor.firstValue.onNext(RevocationCheckRequest(listOf(aliceCert), trustStore, RevocationMode.HARD_FAIL), result)
        assertThat(result.getOrThrow().status).isEqualTo(Active())
    }

    @Test
    fun `corrupeted certificate causes the future to complete exceptionally`() {
        val result = CompletableFuture<RevocationCheckResponse>()
        processor.firstValue.onNext(RevocationCheckRequest(listOf(corruptedAliceCert), trustStore, RevocationMode.HARD_FAIL), result)
        assertThrows<ExecutionException> { result.get() }
    }

    @Test
    @Disabled("Disabling temporarily until CORE-11411 is completed.")
    fun `revoked certificate fails validation with HARD FAIL mode`() {
        val result = CompletableFuture<RevocationCheckResponse>()
        processor.firstValue.onNext(RevocationCheckRequest(listOf(revokedBobCert), trustStore, RevocationMode.HARD_FAIL), result)
        assertThat(result.getOrThrow().status).isInstanceOf(Revoked::class.java)
    }

    @Test
    @Disabled("Disabling temporarily until CORE-11411 is completed.")
    fun `revoked certificate fails validation with SOFT FAIL mode`() {
        val resultFuture = CompletableFuture<RevocationCheckResponse>()
        processor.firstValue.onNext(RevocationCheckRequest(listOf(revokedBobCert), trustStore, RevocationMode.SOFT_FAIL), resultFuture)
        assertThat(resultFuture.getOrThrow().status).isInstanceOf(Revoked::class.java)
    }

    @Test
    fun `if truststore is wrong validation fails`() {
        val resultFuture = CompletableFuture<RevocationCheckResponse>()
        processor.firstValue.onNext(RevocationCheckRequest(listOf(aliceCert), wrongTrustStore, RevocationMode.HARD_FAIL), resultFuture)
        assertThat(resultFuture.getOrThrow().status).isInstanceOf(Revoked::class.java)
    }
}
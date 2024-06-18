package net.corda.p2p.gateway.certificates

import net.corda.data.p2p.gateway.certificates.RevocationCheckRequest
import net.corda.data.p2p.gateway.certificates.RevocationCheckResponse
import net.corda.data.p2p.gateway.certificates.RevocationMode
import net.corda.lifecycle.domino.logic.util.RPCSubscriptionDominoTile
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture

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
        RevocationChecker(subscriptionFactory, mock(), mock(), mock())
    }

    @AfterEach
    fun tearDown() {
        mockDominoTile.close()
    }

    @Test
    fun `if revocation mode is null the future is completed exceptionally`() {
        val revocationCheckRequest = mock<RevocationCheckRequest> {
            whenever(mock.mode).thenReturn(null)
        }
        val mockFuture = mock<CompletableFuture<RevocationCheckResponse>>()
        processor.firstValue.onNext(revocationCheckRequest, mockFuture)
        verify(mockFuture).completeExceptionally(any())
    }

    @Test
    fun `if trust store is null the future is completed exceptionally`() {
        val revocationCheckRequest = mock<RevocationCheckRequest> {
            whenever(mock.mode).thenReturn(RevocationMode.HARD_FAIL)
            whenever(mock.trustedCertificates).thenReturn(null)
        }
        val mockFuture = mock<CompletableFuture<RevocationCheckResponse>>()
        processor.firstValue.onNext(revocationCheckRequest, mockFuture)
        verify(mockFuture).completeExceptionally(any())
    }
}
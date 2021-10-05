package net.corda.crypto.service.rpc

import net.corda.crypto.service.CryptoFactory
import net.corda.data.crypto.wire.signing.WireSigningRequest
import net.corda.data.crypto.wire.signing.WireSigningResponse
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SigningServiceRpcSubTests {
    private lateinit var sub: RPCSubscription<WireSigningRequest, WireSigningResponse>
    private lateinit var subscriptionFactory: SubscriptionFactory
    private lateinit var cryptoFactory: CryptoFactory

    @BeforeEach
    fun setup() {
        sub = mock()
        subscriptionFactory = mock()
        cryptoFactory = mock()
        whenever(
            subscriptionFactory.createRPCSubscription<WireSigningRequest, WireSigningResponse>(any(), any(), any())
        ).thenReturn(sub)
    }

    @Test
    @Timeout(5)
    fun `Should create RPC subscription when configuration is available`() {
        val signingRpc = SigningServiceRpcSub(
            subscriptionFactory,
            cryptoFactory
        )
        assertFalse(signingRpc.isRunning)
        signingRpc.start()
        assertTrue(signingRpc.isRunning)
        signingRpc.handleConfigEvent(mock())
        assertTrue(signingRpc.isRunning)
        Mockito
            .verify(subscriptionFactory, times(1))
            .createRPCSubscription<WireSigningRequest, WireSigningResponse>(any(), any(), any())
        Mockito.verify(sub, times(1)).start()
        Mockito.verify(sub, never()).stop()
        signingRpc.handleConfigEvent(mock())
        Mockito
            .verify(subscriptionFactory, times(2))
            .createRPCSubscription<WireSigningRequest, WireSigningResponse>(any(), any(), any())
        Mockito.verify(sub, times(2)).start()
        Mockito.verify(sub, times(1)).stop()
        signingRpc.stop()
        Mockito.verify(sub, times(2)).stop()
        assertFalse(signingRpc.isRunning)
    }


    @Test
    @Timeout(5)
    fun `Should not fail stopping if RPC subscription never createde`() {
        val signingRpc = SigningServiceRpcSub(
            subscriptionFactory,
            cryptoFactory
        )
        signingRpc.stop()
    }
}
package net.corda.crypto.service.rpc.ops

import net.corda.crypto.service.SigningServiceFactory
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

class CryptoOpsRpcSubTests {
    private lateinit var sub: RPCSubscription<WireSigningRequest, WireSigningResponse>
    private lateinit var subscriptionFactory: SubscriptionFactory
    private lateinit var signingFactory: SigningServiceFactory

    @BeforeEach
    fun setup() {
        sub = mock()
        subscriptionFactory = mock()
        signingFactory = mock()
        whenever(
            subscriptionFactory.createRPCSubscription<WireSigningRequest, WireSigningResponse>(any(), any(), any())
        ).thenReturn(sub)
    }

    @Test
    @Timeout(5)
    fun `Should create RPC subscription when configuration is available`() {
        val opsSub = CryptoOpsRpcSub(
            subscriptionFactory,
            signingFactory
        )
        assertFalse(opsSub.isRunning)
        opsSub.start()
        assertTrue(opsSub.isRunning)
        opsSub.handleConfigEvent(mock())
        assertTrue(opsSub.isRunning)
        Mockito
            .verify(subscriptionFactory, times(1))
            .createRPCSubscription<WireSigningRequest, WireSigningResponse>(any(), any(), any())
        Mockito.verify(sub, times(1)).start()
        Mockito.verify(sub, never()).stop()
        opsSub.handleConfigEvent(mock())
        Mockito
            .verify(subscriptionFactory, times(2))
            .createRPCSubscription<WireSigningRequest, WireSigningResponse>(any(), any(), any())
        Mockito.verify(sub, times(2)).start()
        Mockito.verify(sub, times(1)).stop()
        opsSub.stop()
        Mockito.verify(sub, times(2)).stop()
        assertFalse(opsSub.isRunning)
    }


    @Test
    @Timeout(5)
    fun `Should not fail stopping if RPC subscription never created`() {
        val opsSub = CryptoOpsRpcSub(
            subscriptionFactory,
            signingFactory
        )
        opsSub.stop()
    }
}
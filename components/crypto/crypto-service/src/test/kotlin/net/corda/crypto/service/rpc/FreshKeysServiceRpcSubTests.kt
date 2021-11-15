package net.corda.crypto.service.rpc

import net.corda.crypto.service.CryptoFactory
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysRequest
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysResponse
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

class FreshKeysServiceRpcSubTests {
    private lateinit var sub: RPCSubscription<WireFreshKeysRequest, WireFreshKeysResponse>
    private lateinit var subscriptionFactory: SubscriptionFactory
    private lateinit var cryptoFactory: CryptoFactory

    @BeforeEach
    fun setup() {
        sub = mock()
        subscriptionFactory = mock()
        cryptoFactory = mock()
        whenever(
            subscriptionFactory.createRPCSubscription<WireFreshKeysRequest, WireFreshKeysResponse>(any(), any(), any())
        ).thenReturn(sub)
    }

    @Test
    @Timeout(5)
    fun `Should create RPC subscription when configuration is available`() {
        val freshKeysRpc = FreshKeysServiceRpcSub(
            subscriptionFactory,
            cryptoFactory
        )
        assertFalse(freshKeysRpc.isRunning)
        freshKeysRpc.start()
        assertTrue(freshKeysRpc.isRunning)
        freshKeysRpc.handleConfigEvent(mock())
        assertTrue(freshKeysRpc.isRunning)
        Mockito
            .verify(subscriptionFactory, times(1))
            .createRPCSubscription<WireFreshKeysRequest, WireFreshKeysResponse>(any(), any(), any())
        Mockito.verify(sub, times(1)).start()
        Mockito.verify(sub, never()).stop()
        freshKeysRpc.handleConfigEvent(mock())
        Mockito
            .verify(subscriptionFactory, times(2))
            .createRPCSubscription<WireFreshKeysRequest, WireFreshKeysResponse>(any(), any(), any())
        Mockito.verify(sub, times(2)).start()
        Mockito.verify(sub, times(1)).stop()
        freshKeysRpc.stop()
        Mockito.verify(sub, times(2)).stop()
        assertFalse(freshKeysRpc.isRunning)
    }


    @Test
    @Timeout(5)
    fun `Should not fail stopping if RPC subscription never createde`() {
        val freshKeysRpc = FreshKeysServiceRpcSub(
            subscriptionFactory,
            cryptoFactory
        )
        freshKeysRpc.stop()
    }
}
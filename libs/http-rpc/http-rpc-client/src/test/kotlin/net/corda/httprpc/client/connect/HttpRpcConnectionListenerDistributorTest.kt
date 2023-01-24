package net.corda.httprpc.client.connect

import net.corda.httprpc.RestResource
import net.corda.httprpc.client.HttpRpcConnectionListener
import net.corda.httprpc.client.auth.credentials.CredentialsProvider
import net.corda.httprpc.test.TestHealthCheckAPI
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class HttpRpcConnectionListenerDistributorTest {
    private val credentialsProvider: CredentialsProvider = mock()

    private fun <I : RestResource> mockListener() = mock<HttpRpcConnectionListener<I>>().also {
        doNothing().whenever(it).onConnect(argThat { this.credentialsProvider == credentialsProvider })
        doNothing().whenever(it).onDisconnect(argThat { this.credentialsProvider == credentialsProvider })
        doNothing().whenever(it).onPermanentFailure(argThat { this.credentialsProvider == credentialsProvider })
    }

    private fun <I : RestResource> mockErrorListener() = mock<HttpRpcConnectionListener<I>>().also {
        doThrow(RuntimeException()).whenever(it).onConnect(argThat { this.credentialsProvider == credentialsProvider })
        doThrow(RuntimeException()).whenever(it).onDisconnect(argThat { this.credentialsProvider == credentialsProvider })
        doThrow(RuntimeException()).whenever(it).onPermanentFailure(argThat { this.credentialsProvider == credentialsProvider })
    }

    @Test
    fun `should send onConnect to all listeners`() {
        val listener1 = mockListener<TestHealthCheckAPI>()
        val listener2 = mockListener<TestHealthCheckAPI>()
        val distributor = HttpRpcConnectionListenerDistributor(
            listOf(listener1, listener2), credentialsProvider
        )

        distributor.onConnect()

        // Second onConnect should have no effect as we are already connected
        distributor.onConnect()

        verify(listener1, times(1)).onConnect(argThat { this.credentialsProvider == credentialsProvider })
        verify(listener2, times(1)).onConnect(argThat { this.credentialsProvider == credentialsProvider })
    }

    @Test
    fun `should send onConnect to all listeners while safely handling errors`() {
        val listener1 = mockErrorListener<TestHealthCheckAPI>()
        val listener2 = mockListener<TestHealthCheckAPI>()
        val distributor = HttpRpcConnectionListenerDistributor(
            listOf(listener1, listener2), credentialsProvider
        )

        distributor.onConnect()

        verify(listener1, times(1)).onConnect(argThat { this.credentialsProvider == credentialsProvider })
        verify(listener2, times(1)).onConnect(argThat { this.credentialsProvider == credentialsProvider })
    }

    @Test
    fun `should send onDisconnect to all listeners if connection is not null`() {
        val listener1 = mockListener<TestHealthCheckAPI>()
        val listener2 = mockListener<TestHealthCheckAPI>()
        val distributor = HttpRpcConnectionListenerDistributor(
            listOf(listener1, listener2), credentialsProvider
        )
        distributor.connectionOpt = mock()

        distributor.onConnect()
        distributor.onDisconnect(Throwable())

        verify(listener1, times(1)).onDisconnect(argThat { this.credentialsProvider == credentialsProvider })
        verify(listener2, times(1)).onDisconnect(argThat { this.credentialsProvider == credentialsProvider })
    }

    @Test
    fun `should send onDisconnect to all listeners while safely handling errors if connection is not null`() {
        val listener1 = mockErrorListener<TestHealthCheckAPI>()
        val listener2 = mockListener<TestHealthCheckAPI>()
        val distributor = HttpRpcConnectionListenerDistributor(
            listOf(listener1, listener2), credentialsProvider
        )
        distributor.connectionOpt = mock()
        distributor.onConnect()
        val throwable = Throwable()
        distributor.onDisconnect(throwable)

        verify(listener1, times(1)).onDisconnect(
            argThat { this.credentialsProvider == credentialsProvider && this.throwableOpt === throwable })
        verify(listener2, times(1)).onDisconnect(
            argThat { this.credentialsProvider == credentialsProvider && this.throwableOpt === throwable })
    }

    @Test
    fun `should send onDisconnect to no listeners if connection is null`() {
        val listener1 = mockListener<TestHealthCheckAPI>()
        val listener2 = mockListener<TestHealthCheckAPI>()
        val distributor = HttpRpcConnectionListenerDistributor(
            listOf(listener1, listener2), credentialsProvider
        )
        distributor.connectionOpt = null

        distributor.onConnect()
        distributor.onDisconnect(Throwable())

        verify(listener1, times(0)).onDisconnect(argThat { this.credentialsProvider == credentialsProvider })
        verify(listener2, times(0)).onDisconnect(argThat { this.credentialsProvider == credentialsProvider })
    }
}

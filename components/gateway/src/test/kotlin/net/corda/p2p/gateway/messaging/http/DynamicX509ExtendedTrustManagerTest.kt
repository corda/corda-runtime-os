package net.corda.p2p.gateway.messaging.http

import net.corda.p2p.gateway.messaging.RevocationConfig
import net.corda.p2p.gateway.messaging.RevocationConfigMode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.PKIXBuilderParameters
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager

class DynamicX509ExtendedTrustManagerTest {

    private val mockX509ExtendedTrustManager = mock<X509ExtendedTrustManager>()
    private val secondMockX509ExtendedTrustManager = mock<X509ExtendedTrustManager>()
    private val mockTrustManagerFactory = mock<TrustManagerFactory> {
        on { trustManagers } doReturn arrayOf(mockX509ExtendedTrustManager, secondMockX509ExtendedTrustManager)
    }
    private val mockTrustStore = mock<KeyStore>()
    private val trustStoresMap = mock<TrustStoresMap> {
        on { getTrustStores() } doReturn listOf(mockTrustStore)
    }
    private val dynamicX509ExtendedTrustManager = DynamicX509ExtendedTrustManager(
        trustStoresMap,
        RevocationConfig(RevocationConfigMode.OFF),
        mockTrustManagerFactory
    )
    private val pkixBuilderParameters = Mockito.mockConstruction(PKIXBuilderParameters::class.java)

    @AfterEach
    fun cleanup() {
        pkixBuilderParameters.close()
    }

    @Test
    fun `if any inner X509ExtendedTrustManager does not throw a CertificateException then checkClientTrusted succeeds`() {
        whenever(mockX509ExtendedTrustManager.checkClientTrusted(any(), any())).thenThrow(CertificateException())
        doNothing().`when`(secondMockX509ExtendedTrustManager).checkClientTrusted(any(), any())

        dynamicX509ExtendedTrustManager.checkClientTrusted(arrayOf(mock()), "")
    }

    @Test
    fun `if any inner X509ExtendedTrustManager does not throw a CertificateException then checkClientTrusted with Socket succeeds`() {
        whenever(mockX509ExtendedTrustManager.checkClientTrusted(any(), any(), any<Socket>())).thenThrow(CertificateException())
        doNothing().`when`(secondMockX509ExtendedTrustManager).checkClientTrusted(any(), any(), any<Socket>())

        dynamicX509ExtendedTrustManager.checkClientTrusted(arrayOf(mock()), "", mock<Socket>())
    }

    @Test
    fun `if any inner X509ExtendedTrustManager does not throw a CertificateException then checkClientTrusted with SSLEngine succeeds`() {
        whenever(mockX509ExtendedTrustManager.checkClientTrusted(any(), any(), any<SSLEngine>())).thenThrow(CertificateException())
        doNothing().`when`(secondMockX509ExtendedTrustManager).checkClientTrusted(any(), any(), any<SSLEngine>())

        dynamicX509ExtendedTrustManager.checkClientTrusted(arrayOf(mock()), "", mock<SSLEngine>())
    }

    @Test
    fun `if all inner X509ExtendedTrustManager throw a CertificateException then checkClientTrusted throws`() {
        whenever(mockX509ExtendedTrustManager.checkClientTrusted(any(), any())).thenThrow(CertificateException())
        whenever(secondMockX509ExtendedTrustManager.checkClientTrusted(any(), any())).thenThrow(CertificateException())

        assertThrows<CertificateException> { dynamicX509ExtendedTrustManager.checkClientTrusted(arrayOf(mock()), "") }
    }

    @Test
    fun `if all inner X509ExtendedTrustManager throw a CertificateException then checkClientTrusted with Socket throws`() {
        whenever(mockX509ExtendedTrustManager.checkClientTrusted(any(), any(), any<Socket>())).thenThrow(CertificateException())
        whenever(secondMockX509ExtendedTrustManager.checkClientTrusted(any(), any(), any<Socket>())).thenThrow(CertificateException())

        assertThrows<CertificateException> {
            dynamicX509ExtendedTrustManager.checkClientTrusted(arrayOf(mock()), "", mock<Socket>())
        }
    }

    @Test
    fun `if all inner X509ExtendedTrustManager throw a CertificateException then checkClientTrusted with SSLEngine throws`() {
        whenever(mockX509ExtendedTrustManager.checkClientTrusted(any(), any(), any<SSLEngine>())).thenThrow(CertificateException())
        whenever(secondMockX509ExtendedTrustManager.checkClientTrusted(any(), any(), any<SSLEngine>())).thenThrow(CertificateException())

        assertThrows<CertificateException> {
            dynamicX509ExtendedTrustManager.checkClientTrusted(arrayOf(mock()), "", mock<SSLEngine>())
        }
    }

    @Test
    fun `checkServerTrusted throws an InvalidStateException`() {
        assertThrows<IllegalStateException> {
            dynamicX509ExtendedTrustManager.checkServerTrusted(arrayOf(mock()), "")
        }
    }

    @Test
    fun `checkServerTrusted with Socket throws an InvalidStateException`() {
        assertThrows<IllegalStateException> {
            dynamicX509ExtendedTrustManager.checkServerTrusted(arrayOf(mock()), "", mock<Socket>())
        }
    }

    @Test
    fun `checkServerTrusted with SSLEngine throws an InvalidStateException`() {
        assertThrows<IllegalStateException> {
            dynamicX509ExtendedTrustManager.checkServerTrusted(arrayOf(mock()), "", mock<SSLEngine>())
        }
    }
}
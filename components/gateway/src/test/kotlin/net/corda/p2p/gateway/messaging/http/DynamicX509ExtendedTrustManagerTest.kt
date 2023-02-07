package net.corda.p2p.gateway.messaging.http

import net.corda.p2p.gateway.messaging.RevocationConfig
import net.corda.p2p.gateway.messaging.RevocationConfigMode
import net.corda.p2p.gateway.messaging.mtls.DynamicCertificateSubjectStore
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.PKIXBuilderParameters
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.security.auth.x500.X500Principal

class DynamicX509ExtendedTrustManagerTest {

    private val mockX509ExtendedTrustManager = mock<X509ExtendedTrustManager> {
        on { checkClientTrusted(any(), any()) } doAnswer {}
        on { checkClientTrusted(any(), any(), anyOrNull<Socket>()) } doAnswer {}
        on { checkClientTrusted(any(), any(), anyOrNull<SSLEngine>()) } doAnswer {}
    }
    private val secondMockX509ExtendedTrustManager = mock<X509ExtendedTrustManager> {
        on { checkClientTrusted(any(), any()) } doAnswer {}
        on { checkClientTrusted(any(), any(), anyOrNull<Socket>()) } doAnswer {}
        on { checkClientTrusted(any(), any(), anyOrNull<SSLEngine>()) } doAnswer {}
    }
    private val validCertificateSubject = MemberX500Name.parse("O=Alice,L=London,C=GB")
    private val validCertificate = mock<X509Certificate> {
        on { subjectX500Principal } doReturn validCertificateSubject.x500Principal
    }
    private val dynamicCertificateSubjectStore = mock<DynamicCertificateSubjectStore> {
        on { subjectAllowed(validCertificateSubject) } doReturn true
    }
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
        dynamicCertificateSubjectStore,
        mockTrustManagerFactory,
    )
    private val pkixBuilderParameters = Mockito.mockConstruction(PKIXBuilderParameters::class.java)

    @AfterEach
    fun cleanup() {
        pkixBuilderParameters.close()
    }

    @Test
    fun `if any inner X509ExtendedTrustManager does not throw a CertificateException then checkClientTrusted succeeds`() {
        whenever(mockX509ExtendedTrustManager.checkClientTrusted(any(), any())).thenThrow(CertificateException())

        dynamicX509ExtendedTrustManager.checkClientTrusted(arrayOf(validCertificate), "")
    }

    @Test
    fun `if any inner X509ExtendedTrustManager does not throw a CertificateException then checkClientTrusted with Socket succeeds`() {
        whenever(mockX509ExtendedTrustManager.checkClientTrusted(any(), any(), any<Socket>())).thenThrow(CertificateException())

        dynamicX509ExtendedTrustManager.checkClientTrusted(arrayOf(validCertificate), "", mock<Socket>())
    }

    @Test
    fun `if any inner X509ExtendedTrustManager does not throw a CertificateException then checkClientTrusted with SSLEngine succeeds`() {
        whenever(mockX509ExtendedTrustManager.checkClientTrusted(any(), any(), any<SSLEngine>())).thenThrow(CertificateException())

        dynamicX509ExtendedTrustManager.checkClientTrusted(arrayOf(validCertificate), "", mock<SSLEngine>())
    }

    @Test
    fun `if all inner X509ExtendedTrustManager throw a CertificateException then checkClientTrusted throws`() {
        whenever(mockX509ExtendedTrustManager.checkClientTrusted(any(), any())).thenThrow(CertificateException())
        whenever(secondMockX509ExtendedTrustManager.checkClientTrusted(any(), any())).thenThrow(CertificateException())

        assertThrows<CertificateException> { dynamicX509ExtendedTrustManager.checkClientTrusted(arrayOf(validCertificate), "") }
    }

    @Test
    fun `if all inner X509ExtendedTrustManager throw a CertificateException then checkClientTrusted with Socket throws`() {
        whenever(mockX509ExtendedTrustManager.checkClientTrusted(any(), any(), any<Socket>())).thenThrow(CertificateException())
        whenever(secondMockX509ExtendedTrustManager.checkClientTrusted(any(), any(), any<Socket>())).thenThrow(CertificateException())

        assertThrows<CertificateException> {
            dynamicX509ExtendedTrustManager.checkClientTrusted(arrayOf(validCertificate), "", mock<Socket>())
        }
    }

    @Test
    fun `if all inner X509ExtendedTrustManager throw a CertificateException then checkClientTrusted with SSLEngine throws`() {
        whenever(mockX509ExtendedTrustManager.checkClientTrusted(any(), any(), any<SSLEngine>())).thenThrow(CertificateException())
        whenever(secondMockX509ExtendedTrustManager.checkClientTrusted(any(), any(), any<SSLEngine>())).thenThrow(CertificateException())

        assertThrows<CertificateException> {
            dynamicX509ExtendedTrustManager.checkClientTrusted(arrayOf(validCertificate), "", mock<SSLEngine>())
        }
    }

    @Test
    fun `checkServerTrusted throws an InvalidStateException`() {
        assertThrows<IllegalStateException> {
            dynamicX509ExtendedTrustManager.checkServerTrusted(arrayOf(validCertificate), "")
        }
    }

    @Test
    fun `checkServerTrusted with Socket throws an InvalidStateException`() {
        assertThrows<IllegalStateException> {
            dynamicX509ExtendedTrustManager.checkServerTrusted(arrayOf(validCertificate), "", mock<Socket>())
        }
    }

    @Test
    fun `checkServerTrusted with SSLEngine throws an InvalidStateException`() {
        assertThrows<IllegalStateException> {
            dynamicX509ExtendedTrustManager.checkServerTrusted(arrayOf(validCertificate), "", mock<SSLEngine>())
        }
    }

    @Test
    fun `getAcceptedIssuers return the accepted issuers`() {
        val firstStoreCertificates = (1..3).map {
            mock<X509Certificate>()
        }
        val secondStoreCertificates = (1..2).map {
            mock<X509Certificate>()
        }
        whenever(mockX509ExtendedTrustManager.acceptedIssuers)
            .doReturn(firstStoreCertificates.toTypedArray())
        whenever(secondMockX509ExtendedTrustManager.acceptedIssuers)
            .doReturn(secondStoreCertificates.toTypedArray())

        val issuers = dynamicX509ExtendedTrustManager.acceptedIssuers

        assertThat(issuers).containsAll(firstStoreCertificates)
            .containsAll(secondStoreCertificates)
    }

    @Test
    fun `checkClientTrusted will fail with null certificate chain`() {
        assertThrows<CertificateException> {
            dynamicX509ExtendedTrustManager.checkClientTrusted(null, "")
        }
    }

    @Test
    fun `checkClientTrusted will fail with empty certificate chain`() {
        assertThrows<CertificateException> {
            dynamicX509ExtendedTrustManager.checkClientTrusted(emptyArray(), "")
        }
    }

    @Test
    fun `checkClientTrusted will fail with invalid certificate subject`() {
        val certificate = mock<X509Certificate> {
            on { subjectX500Principal } doReturn X500Principal("C=Alice")
        }
        assertThrows<CertificateException> {
            dynamicX509ExtendedTrustManager.checkClientTrusted(arrayOf(certificate), "")
        }
    }

    @Test
    fun `checkClientTrusted will fail when the certificate subject is not allowed`() {
        val certificate = mock<X509Certificate> {
            on { subjectX500Principal } doReturn X500Principal("O=Bob,L=London,C=GB")
        }
        assertThrows<CertificateException> {
            dynamicX509ExtendedTrustManager.checkClientTrusted(arrayOf(certificate), "")
        }
    }
}
package net.corda.p2p.gateway.messaging.http

import net.corda.p2p.gateway.certificates.RevocationChecker
import net.corda.p2p.gateway.messaging.RevocationConfig
import net.corda.p2p.gateway.messaging.SslConfiguration
import net.corda.p2p.gateway.messaging.TlsType
import net.corda.p2p.gateway.messaging.mtls.DynamicCertificateSubjectStore
import net.corda.v5.base.types.MemberX500Name
import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.CertPathTrustManagerParameters
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager

internal class DynamicX509ExtendedTrustManager(
    private val trustStoresMap: TrustStoresMap,
    private val revocationConfig: RevocationConfig,
    private val dynamicCertificateSubjectStore: DynamicCertificateSubjectStore,
    private val trustManagerFactory: TrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
) : X509ExtendedTrustManager() {
    companion object {
        private val wrongUsageMessage = this::class.java.simpleName + " can only be used by the gateway server."
        private const val invalidClientMessage = "None of the possible trust roots were valid for the client certificate. Error(s): "

        fun createTrustManagerIfNeeded(
            sslConfiguration: SslConfiguration,
            trustStoresMap: TrustStoresMap,
            dynamicCertificateSubjectStore: DynamicCertificateSubjectStore,
        ): X509ExtendedTrustManager? {
            return if (sslConfiguration.tlsType == TlsType.MUTUAL) {
                DynamicX509ExtendedTrustManager(
                    trustStoresMap,
                    sslConfiguration.revocationCheck,
                    dynamicCertificateSubjectStore,
                )
            } else {
                null
            }
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        checkClientCertificate(chain) {
            it.checkClientTrusted(chain, authType, socket)
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        checkClientCertificate(chain) {
            it.checkClientTrusted(chain, authType, engine)
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        checkClientCertificate(chain) {
            it.checkClientTrusted(chain, authType)
        }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        throw IllegalStateException(wrongUsageMessage)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        throw IllegalStateException(wrongUsageMessage)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw IllegalStateException(wrongUsageMessage)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        // We assume here that all the trust stores were issued by the same CA.
        return getAllX509TrustManagers().flatMap {
            it.acceptedIssuers.toList()
        }.toTypedArray()
    }

    @Suppress("ThrowsCount")
    private fun validateClientCertificateChain(chain: Array<out X509Certificate>?) {
        if (chain == null) {
            throw CertificateException("Can not accept null client certificate chain.")
        }
        if (chain.isEmpty()) {
            throw CertificateException("Can not accept empty client certificate chain.")
        }
        val certificateSubject = try {
            MemberX500Name.build(chain.first().subjectX500Principal)
        } catch (e: IllegalArgumentException) {
            throw CertificateException(
                "Client certificate subject ${chain.first().subjectX500Principal} is not a valid subject: $e",
                e
            )
        }
        if (!dynamicCertificateSubjectStore.subjectAllowed(certificateSubject)) {
            throw CertificateException("Client certificate with subject $certificateSubject is not allowed.")
        }
    }

    private fun validateTrustedCertificates(verify: (X509ExtendedTrustManager) -> Unit) {
        val exceptionMessages = getAllX509TrustManagers().map {
            try {
                verify(it)
                return
            } catch (except: CertificateException) {
                except
            }
        }.mapNotNull {
            it.message
        }
        throw CertificateException(invalidClientMessage + exceptionMessages.joinToString())
    }

    private fun checkClientCertificate(
        chain: Array<out X509Certificate>?,
        verify: (X509ExtendedTrustManager) -> Unit
    ) {
        validateClientCertificateChain(chain)
        validateTrustedCertificates(verify)
    }

    private fun KeyStore.x509ExtendedTrustManager(): List<X509ExtendedTrustManager> {
        val pkixParams = RevocationChecker.getCertCheckingParameters(this, revocationConfig)
        trustManagerFactory.init(CertPathTrustManagerParameters(pkixParams))
        return trustManagerFactory.trustManagers.filterIsInstance<X509ExtendedTrustManager>()
    }

    private fun getAllX509TrustManagers(): List<X509ExtendedTrustManager> =
        trustStoresMap.getTrustStores().flatMap { it.x509ExtendedTrustManager() }
}

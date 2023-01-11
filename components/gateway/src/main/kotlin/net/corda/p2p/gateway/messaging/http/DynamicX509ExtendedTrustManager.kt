package net.corda.p2p.gateway.messaging.http

import net.corda.p2p.gateway.certificates.RevocationChecker
import net.corda.p2p.gateway.messaging.RevocationConfig
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
    private val trustManagerFactory: TrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
): X509ExtendedTrustManager() {
    private companion object {
        val wrongUsageMessage = this::class.java.simpleName + " can only be used by the gateway server."
        const val invalidClientMessage = "None of the possible trust roots were valid for the client certificate. Error(s): "
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        val exceptionMessages = mutableSetOf<String>()
        trustStoresMap.getTrustStores().flatMap { it.x509ExtendedTrustManager() }.forEach {
            if (doesNotThrowCertificateException(exceptionMessages) { it.checkClientTrusted(chain, authType, socket) }) {
                return
            }
        }
        throw CertificateException(invalidClientMessage + exceptionMessages.joinToString())
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        val exceptionMessages = mutableSetOf<String>()
        trustStoresMap.getTrustStores().flatMap { it.x509ExtendedTrustManager() }.forEach {
            if (doesNotThrowCertificateException(exceptionMessages) { it.checkClientTrusted(chain, authType, engine) }) {
                return
            }
        }
        throw CertificateException(invalidClientMessage + exceptionMessages.joinToString())
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val exceptionMessages = mutableSetOf<String>()
        trustStoresMap.getTrustStores().flatMap { it.x509ExtendedTrustManager() }.forEach {
            if (doesNotThrowCertificateException(exceptionMessages) { it.checkClientTrusted(chain, authType) }) {
                return
            }
        }
        throw CertificateException(invalidClientMessage + exceptionMessages.joinToString())
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
         return trustStoresMap.getTrustStores().flatMap {
             it.x509ExtendedTrustManager() }
         .map {
             it.acceptedIssuers.toList()
         }.flatten().toTypedArray()
    }

    private fun doesNotThrowCertificateException(exceptionMessages: MutableSet<String>, function : (() -> Unit)): Boolean {
        return try {
            function()
            true
        } catch (except: CertificateException) {
            except.message?.let { message -> exceptionMessages.add(message) }
            false
        }
    }

    private fun KeyStore.x509ExtendedTrustManager(): List<X509ExtendedTrustManager> {
        val pkixParams = RevocationChecker.getCertCheckingParameters(this, revocationConfig)
        trustManagerFactory.init(CertPathTrustManagerParameters(pkixParams))
        return trustManagerFactory.trustManagers.filterIsInstance<X509ExtendedTrustManager>()
    }
}
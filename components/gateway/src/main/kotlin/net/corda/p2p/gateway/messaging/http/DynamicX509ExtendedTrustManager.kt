package net.corda.p2p.gateway.messaging.http

import net.corda.p2p.gateway.messaging.RevocationConfig
import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedTrustManager

internal class DynamicX509ExtendedTrustManager(
    private val trustStoresMap: TrustStoresMap,
    private val revocationCheck: RevocationConfig,
) : X509ExtendedTrustManager() {
    private val allManagers: Sequence<X509ExtendedTrustManager>
        get() = trustStoresMap.allTrustedCertificates
            .asSequence()
            .mapNotNull {
                it.trustManagers(revocationCheck)
            }.filterIsInstance<X509ExtendedTrustManager>()

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        for (manager in allManagers) {
            try {
                manager.checkClientTrusted(chain, authType, socket)
                return
            } catch (e: CertificateException) {
                // Try the next manager
            }
        }
        throw CertificateException("Can not find manager")
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        for (manager in allManagers) {
            try {
                manager.checkClientTrusted(chain, authType, engine)
                return
            } catch (e: CertificateException) {
                // Try the next manager
            }
        }
        throw CertificateException("Can not find manager")
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        for (manager in allManagers) {
            try {
                manager.checkClientTrusted(chain, authType)
                return
            } catch (e: CertificateException) {
                // Try the next manager
            }
        }
        throw CertificateException("Can not find manager")
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        for (manager in allManagers) {
            try {
                manager.checkServerTrusted(chain, authType, socket)
                return
            } catch (e: CertificateException) {
                // Try the next manager
            }
        }
        throw CertificateException("Can not find manager")
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        for (manager in allManagers) {
            try {
                manager.checkServerTrusted(chain, authType, engine)
                return
            } catch (e: CertificateException) {
                // Try the next manager
            }
        }
        throw CertificateException("Can not find manager")
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        for (manager in allManagers) {
            try {
                manager.checkServerTrusted(chain, authType)
                return
            } catch (e: CertificateException) {
                // Try the next manager
            }
        }
        throw CertificateException("Can not find manager")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return allManagers.flatMap { it.acceptedIssuers.toList() }.toList().toTypedArray()
    }
}

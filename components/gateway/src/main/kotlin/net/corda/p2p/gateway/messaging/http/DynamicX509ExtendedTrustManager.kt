package net.corda.p2p.gateway.messaging.http

import net.corda.p2p.gateway.messaging.RevocationConfig
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

internal class DynamicX509ExtendedTrustManager(
    private val trustStoresMap: TrustStoresMap,
    private val revocationCheck: RevocationConfig,
    private val clientCertificatesAllowList: ClientCertificatesAllowList,
) : X509ExtendedTrustManager() {

    @Suppress("NestedBlockDepth", "ThrowsCount")
    private fun checkClientTrusted(chain: Array<out X509Certificate>?, check: (X509TrustManager)->Unit) {
        if(chain == null) {
            throw CertificateException("Null client certificates")
        }
        if(chain.isEmpty()) {
            throw CertificateException("Empty client certificates")
        }
        val subjects = chain.map { it.subjectX500Principal }
        for (managersToGroupId in trustStoresMap.getTrustManagersToGroupId(revocationCheck)) {
            val managers = managersToGroupId.key.filterIsInstance<X509TrustManager>()
            if(clientCertificatesAllowList.allowCertificates(managersToGroupId.value, subjects)) {
                for (manager in managers) {
                    try {
                        check(manager)
                        return
                    } catch (e: CertificateException) {
                        // Try the next manager
                    }
                }
            }
        }
        throw CertificateException("Can not find manager")
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        checkClientTrusted(chain) { manager ->
            if(manager is X509ExtendedTrustManager) {
                manager.checkClientTrusted(chain, authType, socket)
            } else {
                manager.checkClientTrusted(chain, authType)
            }
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        checkClientTrusted(chain) { manager ->
            if(manager is X509ExtendedTrustManager) {
                manager.checkClientTrusted(chain, authType, engine)
            } else {
                manager.checkClientTrusted(chain, authType)
            }
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        checkClientTrusted(chain) { manager ->
            manager.checkClientTrusted(chain, authType)
        }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        throw CordaRuntimeException("The DynamicX509ExtendedTrustManager should only be used in server side")
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        throw CordaRuntimeException("The DynamicX509ExtendedTrustManager should only be used in server side")
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CordaRuntimeException("The DynamicX509ExtendedTrustManager should only be used in server side")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return trustStoresMap
            .getTrustManagersToGroupId(revocationCheck)
            .keys
            .flatten()
            .filterIsInstance<X509TrustManager>()
            .map { it.acceptedIssuers.toList() }
            .flatten()
            .toTypedArray()
    }
}

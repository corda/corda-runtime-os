package net.corda.p2p.gateway.messaging.http

import org.slf4j.LoggerFactory
import java.net.Socket
import java.security.Principal
import javax.net.ssl.SNIMatcher
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509KeyManager

class SNIKeyManager(
    private val keyManager: X509ExtendedKeyManager,
): X509ExtendedKeyManager(),
    X509KeyManager by keyManager {

    private val logger = LoggerFactory.getLogger(SNIKeyManager::class.java)

    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? {
        val matcher = (socket as SSLSocket).sslParameters.sniMatchers.first()
        return chooseServerAlias(keyType, issuers, matcher)
    }

    override fun chooseEngineServerAlias(keyType: String?, issuers: Array<out Principal>?, engine: SSLEngine?): String? {
        val matcher = engine?.sslParameters?.sniMatchers?.first()
        return chooseServerAlias(keyType, issuers, matcher)
    }

    private fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, matcher: SNIMatcher?): String? {
        val aliases = keyManager.getServerAliases(keyType, null)
        if (aliases.isNullOrEmpty()) {
            logger.debug("Keystore doesn't contain any aliases for key type $keyType and issuers $issuers")
            return null
        }

        logger.debug("Checking aliases: ${aliases.joinToString(",")}")
        return (matcher as? HostnameMatcher)?.let { hostMatcher ->
            aliases.firstOrNull { alias ->
                when(hostMatcher.aliasMatch(alias)) {
                    HostnameMatcher.MatchType.NONE -> false
                    HostnameMatcher.MatchType.C4 -> true
                    HostnameMatcher.MatchType.C5 -> {
                        if (issuers == null) {
                            true
                        } else {
                            val issuesrsSet = issuers.toSet()
                            val certificate = keyManager.getCertificateChain(alias).firstOrNull()
                            val issuer = certificate?.issuerX500Principal
                            if (issuer==null) {
                                false
                            } else {
                                issuesrsSet.contains(issuer)
                            }
                        }
                    }
                }
            }
        }
    }
}
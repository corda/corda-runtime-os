package net.corda.p2p.gateway.messaging.http

import org.slf4j.LoggerFactory
import java.net.Socket
import java.security.Principal
import javax.net.ssl.SNIMatcher
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509KeyManager

class SNIKeyManager(private val keyManager: X509ExtendedKeyManager): X509ExtendedKeyManager(),
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
        val aliases = keyManager.getServerAliases(keyType, issuers)
        if (aliases == null || aliases.isEmpty()) {
            logger.debug("Keystore doesn't contain any aliases for key type $keyType and issuers $issuers")
            return null
        }

        logger.debug("Checking aliases: ${aliases.joinToString(",")}")
        matcher?.let {
            val matchedAlias = (it as HostnameMatcher).matchedAlias
            if (aliases.contains(matchedAlias)) {
                logger.debug("Found match for $matchedAlias")
                return matchedAlias
            }
        }

        logger.debug("Unable to find matching alias")
        return null
    }
}
package net.corda.crypto.impl.schememetadata

import net.corda.v5.cipher.suite.schemes.SignatureScheme
import java.security.KeyFactory
import java.security.Provider
import java.util.concurrent.ConcurrentHashMap

class KeyFactoryProvider(private val providers: Map<String, Provider>) {
    private val keyFactoryCache = ConcurrentHashMap<SignatureScheme, KeyFactory>()

    operator fun get(scheme: SignatureScheme): KeyFactory =
        keyFactoryCache.getOrPut(scheme) {
            KeyFactory.getInstance(scheme.algorithmName, providers[scheme.providerName])
        }
}
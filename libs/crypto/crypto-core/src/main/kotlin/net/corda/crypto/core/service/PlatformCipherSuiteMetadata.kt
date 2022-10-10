package net.corda.crypto.core.service

import net.corda.v5.cipher.suite.KeyScheme
import net.corda.v5.cipher.suite.KeySchemeInfo
import net.corda.v5.cipher.suite.SecureRandomProvider
import net.corda.v5.cipher.suite.handlers.encoding.KeyEncodingHandler
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import java.security.KeyFactory
import java.security.Provider

interface PlatformCipherSuiteMetadata : KeyEncodingHandler, SecureRandomProvider {
    val supportedSigningSchemes: Map<KeyScheme, KeySchemeInfo>
    fun findKeyScheme(algorithm: AlgorithmIdentifier): KeyScheme
    fun findKeyFactory(algorithm: AlgorithmIdentifier): KeyFactory
    fun providerFor(scheme: KeyScheme): Provider
    fun providerForDigest(algorithmName: String): Provider
}
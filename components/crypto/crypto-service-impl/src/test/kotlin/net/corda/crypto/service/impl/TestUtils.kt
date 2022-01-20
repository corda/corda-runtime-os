package net.corda.crypto.service.impl

import net.corda.v5.cipher.suite.CipherSchemeMetadata
import java.security.KeyPair
import java.security.KeyPairGenerator

fun generateKeyPair(schemeMetadata: CipherSchemeMetadata, signatureSchemeName: String): KeyPair {
    val scheme = schemeMetadata.findSignatureScheme(signatureSchemeName)
    val keyPairGenerator = KeyPairGenerator.getInstance(
        scheme.algorithmName,
        schemeMetadata.providers.getValue(scheme.providerName)
    )
    if (scheme.algSpec != null) {
        keyPairGenerator.initialize(scheme.algSpec, schemeMetadata.secureRandom)
    } else if (scheme.keySize != null) {
        keyPairGenerator.initialize(scheme.keySize!!, schemeMetadata.secureRandom)
    }
    return keyPairGenerator.generateKeyPair()
}

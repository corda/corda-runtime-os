@file:JvmName("CryptoMockUtils")

package net.corda.crypto.testkit

import net.corda.v5.cipher.suite.CipherSchemeMetadata
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature


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


fun signData(schemeMetadata: CipherSchemeMetadata, keyPair: KeyPair, data: ByteArray): ByteArray {
    val scheme = schemeMetadata.findSignatureScheme(keyPair.public)
    val signature = Signature.getInstance(
        scheme.signatureSpec.signatureName,
        schemeMetadata.providers[scheme.providerName]
    )
    signature.initSign(keyPair.private, schemeMetadata.secureRandom)
    signature.update(data)
    return signature.sign()
}
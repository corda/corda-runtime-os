package net.corda.crypto.client

import net.corda.v5.cipher.suite.CipherSchemeMetadata
import org.bouncycastle.jce.ECNamedCurveTable
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature

fun generateKeyPair(schemeMetadata: CipherSchemeMetadata): KeyPair {
    val keyPairGenerator = KeyPairGenerator.getInstance("EC", schemeMetadata.providers["BC"])
    keyPairGenerator.initialize(ECNamedCurveTable.getParameterSpec("secp256r1"))
    return keyPairGenerator.generateKeyPair()
}

fun sign(schemeMetadata: CipherSchemeMetadata, privateKey: PrivateKey, data: ByteArray): ByteArray {
    val signature = Signature.getInstance("SHA256withECDSA", schemeMetadata.providers["BC"])
    signature.initSign(privateKey, schemeMetadata.secureRandom)
    signature.update(data)
    return signature.sign()
}

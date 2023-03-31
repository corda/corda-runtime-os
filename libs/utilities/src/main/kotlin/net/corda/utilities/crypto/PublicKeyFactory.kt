package net.corda.utilities.crypto

import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import java.io.Reader
import java.security.PublicKey

fun publicKeyFactory(pem: Reader): PublicKey? {
    return PEMParser(pem).use { parser ->
        generateSequence {
            parser.readObject()
        }.map {
            if (it is SubjectPublicKeyInfo) {
                JcaPEMKeyConverter().getPublicKey(it)
            } else {
                null
            }
        }.filterNotNull()
            .firstOrNull()
    }
}

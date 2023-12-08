package net.corda.utilities.crypto

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import java.io.Reader
import java.io.StringWriter
import java.security.Key
import java.security.PrivateKey
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

fun Key.toPem(): String {
    return StringWriter().use { str ->
        JcaPEMWriter(str).use { writer ->
            writer.writeObject(this)
        }
        str.toString()
    }
}

fun privateKeyFactory(pem: Reader): PrivateKey? {
    return PEMParser(pem).use { parser ->
        generateSequence {
            parser.readObject()
        }.map {
            if (it is PrivateKeyInfo) {
                JcaPEMKeyConverter().getPrivateKey(it)
            } else {
                null
            }
        }.filterNotNull()
            .firstOrNull()
    }
}

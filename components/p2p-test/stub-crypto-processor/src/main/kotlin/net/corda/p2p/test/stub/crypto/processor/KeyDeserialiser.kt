package net.corda.p2p.test.stub.crypto.processor

import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import java.security.KeyPair

internal class KeyDeserialiser {
    fun toKeyPair(pem: String): KeyPair {
        return pem.reader().use {
            PEMParser(it).use { parser ->
                generateSequence {
                    parser.readObject()
                }.map {
                    if (it is PEMKeyPair) {
                        JcaPEMKeyConverter().getKeyPair(it)
                    } else {
                        null
                    }
                }.filterNotNull()
                    .firstOrNull()
            }
        } ?: throw CouldNotReadKey(pem)
    }
}

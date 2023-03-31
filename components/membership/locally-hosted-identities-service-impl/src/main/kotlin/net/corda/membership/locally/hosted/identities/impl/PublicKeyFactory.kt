package net.corda.membership.locally.hosted.identities.impl

import net.corda.v5.base.exceptions.CordaRuntimeException
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import java.security.PublicKey

internal fun publicKeyFactory(pem: String): PublicKey {
    return PEMParser(pem.reader()).use { parser ->
        generateSequence {
            parser.readObject()
        }.map {
            if (it is SubjectPublicKeyInfo) {
                JcaPEMKeyConverter().getPublicKey(it)
            } else {
                null
            }
        }.filterNotNull()
            .firstOrNull() ?: throw CordaRuntimeException("Not a public key PEM: $pem")
    }
}

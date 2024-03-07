package net.corda.crypto.test.certificates.generation

import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate

/**
 * A data class that represent a private key and certificate for the corresponding public key.
 */
data class PrivateKeyWithCertificate(
    val privateKey: PrivateKey,
    val certificates: Collection<Certificate>,
) {

    /**
     * Convert the pair to a key store object with password: PASSWORD and alias: "entry".
     */
    fun toKeyStore(): KeyStore {
        val keyStore = KeyStore.getInstance("PKCS12").also { keyStore ->
            keyStore.load(null)
            keyStore.setKeyEntry("entry", privateKey, CertificateAuthority.PASSWORD.toCharArray(), certificates.toTypedArray())
        }
        return keyStore
    }
}

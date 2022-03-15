package net.corda.crypto.test.certificates.generation

import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate

/**
 * A data class that represent a private key and certificate pair.
 */
data class PrivateKeyWithCertificate(
    val privateKey: PrivateKey,
    val certificate: Certificate,
) {

    /**
     * Convert the pair to a key store object with password: PASSWORD and alias: "entry".
     */
    fun toKeyStore(): KeyStore {
        val keyStore = KeyStore.getInstance("PKCS12").also { keyStore ->
            keyStore.load(null)
            keyStore.setKeyEntry("entry", privateKey, CertificateAuthority.PASSWORD.toCharArray(), arrayOf(certificate))
        }
        return keyStore
    }

    /**
     * Convert the certificate to pem String.
     */
    fun certificatePem(): String {
        return certificate.toPem()
    }
}

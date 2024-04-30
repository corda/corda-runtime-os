package net.corda.sdk.packaging.signing

import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.X509Certificate

object CertificateLoader {
    /**
     * Reads trusted certificates from keystore [keyStoreFile]
     */
    fun readCertificates(keyStoreFile: Path, keyStorePass: String): Collection<X509Certificate> {
        val keyStore = KeyStore.getInstance(keyStoreFile.toFile(), keyStorePass.toCharArray())
        return keyStore.aliases().asSequence()
            .filter(keyStore::isCertificateEntry)
            .map { keyStore.getCertificate(it) as X509Certificate }
            .toList()
    }
}

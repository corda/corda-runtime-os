package net.corda.crypto.test.certificates.generation

import java.security.KeyStore

/**
 * A local certificates authority (which has the ability to produce a key store from its own private key).
 */
interface LocalCertificatesAuthority : CertificateAuthority {
    /**
     * Convert the CA certificate and private key to a key store (with password: PASSWORD) with a single
     * alias entry.
     *
     * @param alias The alias of the entry in the key store.
     */
    fun asKeyStore(alias: String): KeyStore
}

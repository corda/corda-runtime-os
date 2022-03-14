package net.corda.crypto.test.certificates.generation

import java.security.KeyStore

/**
 * A local certificates authority (which has the ability to produce a key store from its own private key).
 */
abstract class LocalCertificatesAuthority : StubCertificatesAuthority() {

    /**
     * Convert the CA certificate and private key to a key store (with password: PASSWORD).
     */
    abstract fun createAuthorityKeyStore(alias: String): KeyStore
}

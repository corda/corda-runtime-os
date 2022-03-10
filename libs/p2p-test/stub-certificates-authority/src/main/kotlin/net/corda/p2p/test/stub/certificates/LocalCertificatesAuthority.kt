package net.corda.p2p.test.stub.certificates

import java.security.KeyStore

abstract class LocalCertificatesAuthority : StubCertificatesAuthority() {
    abstract fun createAuthorityKeyStore(alias: String): KeyStore
}

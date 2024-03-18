package net.corda.crypto.test.certificates.generation

import java.security.cert.Certificate

interface RevocableCertificateAuthority: CloseableCertificateAuthority {
    fun revoke(certificate: Certificate)
    fun reintroduce(certificate: Certificate)
}
package net.corda.crypto.delegated.signing

import java.security.cert.Certificate

interface DelegatedCertificatesStore {
    val name: String
    val certificates: Collection<Certificate>
}

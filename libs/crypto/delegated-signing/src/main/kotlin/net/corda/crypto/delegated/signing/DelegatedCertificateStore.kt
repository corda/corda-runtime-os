package net.corda.crypto.delegated.signing

import java.security.cert.Certificate
typealias Alias = String
typealias CertificateChain = Collection<Certificate>
interface DelegatedCertificateStore {
    val aliasToCertificates: Map<Alias, CertificateChain>
}

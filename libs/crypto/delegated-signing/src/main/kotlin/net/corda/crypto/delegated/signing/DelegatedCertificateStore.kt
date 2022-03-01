package net.corda.crypto.delegated.signing

import java.security.cert.Certificate
typealias Alias = String
typealias CertificateChain = Collection<Certificate>
interface DelegatedCertificateStore {
    data class TenantInfo(
        val tenantId: String,
        val certificateChain: CertificateChain,
    )
    val aliasToTenantInfo: Map<Alias, TenantInfo>
}

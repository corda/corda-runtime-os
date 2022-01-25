package net.corda.crypto.delegated.signing

import java.security.Provider
import java.security.Security

class DelegatedSignerInstaller {
    companion object {
        const val RSA_SIGNING_ALGORITHM = "RSASSA-PSS"
    }

    fun findOriginalSignatureProvider(algorithm: String): Provider {
        return Security.getProviders()
            .filter {
                it !is DelegatedKeystoreProvider
            }
            .flatMap {
                it.services
            }.filter {
                it.algorithm == algorithm
            }.firstOrNull {
                it.type == "Signature"
            }?.provider ?: throw SecurityException("Could not find a signature provider for $algorithm")
    }

    fun install(name: String, signer: DelegatedSigner, certificates: Collection<DelegatedCertificatesStore>) {
        DelegatedKeystoreProvider.provider.putServices(name, signer, certificates)
    }
}

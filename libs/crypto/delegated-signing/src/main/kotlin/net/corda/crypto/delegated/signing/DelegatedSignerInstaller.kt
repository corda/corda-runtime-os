package net.corda.crypto.delegated.signing

class DelegatedSignerInstaller {
    companion object {
        const val RSA_SIGNING_ALGORITHM = "RSASSA-PSS"
    }

    fun install(name: String, signer: DelegatedSigner, certificates: DelegatedCertificateStore) {
        DelegatedKeystoreProvider.provider.putServices(name, signer, certificates)
    }
}

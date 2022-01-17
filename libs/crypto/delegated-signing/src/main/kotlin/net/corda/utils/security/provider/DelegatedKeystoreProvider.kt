package net.corda.utils.security.provider

import java.security.Provider

internal class DelegatedKeystoreProvider : Provider(
    PROVIDER_NAME,
    "0.2",
    "JCA/JCE delegated keystore provider",
) {

    companion object {
        internal const val PROVIDER_NAME = "DelegatedKeyStore"
    }

    fun putService(name: String, signingService: DelegatedSigningService) {
        putService(DelegatedKeyStoreService(name, signingService))
    }

    private inner class DelegatedKeyStoreService(
        name: String,
        private val signingService:
        DelegatedSigningService
    ) : Service(
        this@DelegatedKeystoreProvider,
        "KeyStore",
        name,
        "DelegatedKeyStore",
        null,
        null,
    ) {
        override fun newInstance(constructorParameter: Any?): Any {
            return DelegatedKeystore(signingService)
        }
    }
}

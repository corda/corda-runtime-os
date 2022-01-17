package com.r3.corda.utils.provider

import java.security.Provider
import java.security.Security

class DelegatedKeystoreProvider : Provider(
    PROVIDER_NAME,
    "0.2",
    "JCA/JCE delegated keystore provider",
) {

    companion object {
        private const val PROVIDER_NAME = "DelegatedKeyStore"

        @Synchronized
        fun putService(name: String, signingService: DelegatedSigningService) {
            val provider = Security.getProvider(PROVIDER_NAME)
            val delegatedKeystoreProvider = if (provider != null) {
                provider as DelegatedKeystoreProvider
            } else {
                DelegatedKeystoreProvider().apply { Security.addProvider(this) }
            }
            delegatedKeystoreProvider.putService(name, signingService)
        }
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

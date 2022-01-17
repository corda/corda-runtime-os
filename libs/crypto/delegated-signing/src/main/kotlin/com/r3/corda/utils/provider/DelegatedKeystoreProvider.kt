package com.r3.corda.utils.provider

import java.security.NoSuchAlgorithmException
import java.security.Provider
import java.security.Security

class DelegatedKeystoreProvider : Provider(
    PROVIDER_NAME,
    "0.1",
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
        putService(DelegatedKeyStoreService(this, name, signingService))
    }

    private class DelegatedKeyStoreService(
        provider: Provider,
        name: String,
        private val signingService:
            DelegatedSigningService
    ) : Service(
        provider,
        "KeyStore", name, "DelegatedKeyStore", null, null
    ) {
        @Throws(NoSuchAlgorithmException::class)
        override fun newInstance(var1: Any?): Any {
            return DelegatedKeystore(signingService)
        }
    }
}


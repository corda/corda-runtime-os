package net.corda.p2p.gateway.security.delegates

import java.security.KeyStore
import java.security.Provider
import java.security.Security

object SecurityDelegateProvider : Provider(
    SecurityDelegateProvider::class.simpleName,
    "0.2",
    "A delegate keystore provider"
) {
    const val RSA_SINGING_ALGORITHM = "RSASSA-PSS"
    init {
        putService(DelegateKeyStoreService)
        putService(DelegateSignatureService(RSA_SINGING_ALGORITHM, null))
        SigningService.Hash.values().forEach {
            putService(DelegateSignatureService(it.ecName, it))
        }
        this["AlgorithmParameters.EC"] = "sun.security.util.ECParameters"
        Security.insertProviderAt(SecurityDelegateProvider, 1)
    }

    fun createKeyStore(
        service: SigningService
    ): KeyStore {
        return KeyStore.getInstance("CordaDelegateKeyStore").also {
            it.load(DelegateKeyStore.LoadParameter(service))
        }
    }

    private object DelegateKeyStoreService : Service(
        this,
        "KeyStore",
        "CordaDelegateKeyStore",
        DelegateKeyStore::class.qualifiedName,
        null,
        mapOf("SupportedKeyClasses" to DelegatedPrivateKey::class.java.name)
    ) {

        override fun newInstance(constructorParameter: Any?): Any {
            return DelegateKeyStore()
        }
    }

    private class DelegateSignatureService(
        algorithm: String,
        private val defaultHash: SigningService.Hash?
    ) : Service(
        this,
        "Signature",
        algorithm,
        DelegatedSignature::class.java.name,
        null,
        null,
    ) {
        override fun newInstance(constructorParameter: Any?): Any {
            return DelegatedSignature(defaultHash)
        }
    }
}


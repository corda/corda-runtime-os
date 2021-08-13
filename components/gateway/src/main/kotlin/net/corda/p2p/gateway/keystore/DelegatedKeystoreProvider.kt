package net.corda.p2p.gateway.keystore

import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import java.security.Provider
import java.security.Security

/**
 * The Java Security provider with methods to register 'KeyStore' type services providing [DelegatedKeystore] objects.
 * The provider is installed under name 'DelegatedKeyStoreProvider'.
 */
class DelegatedKeystoreProvider : Provider(PROVIDER_NAME, "1.0", "JCA/JCE delegated keystore provider") {

    companion object {
        private const val PROVIDER_NAME = "DelegatedKeyStoreProvider"

        /**
         * Registers [DelegatedKeystoreProvider] under name 'DelegatedKeyStoreProvider' if the provider is not yet registered,
         * and adds to it a services for providing a [DelegatedKeystore] keyStore.
         * @param name - name (algorithm) under which [DelegatedKeystore] will be registered
         * @param signingService - an optional [DelegatedSigningService] for [DelegatedKeystore]
         * @param certStore - source of certificates for [DelegatedKeystore]
         * @return an instance of the delegated keyStore.
         */
        @Synchronized
        fun createKeyStore(name: String, signingService: DelegatedSigningService?, certStore: KeyStore): KeyStore {
            val provider = getProvider()
            provider.putKeyStoreService(name, DelegatedKeystore::class.simpleName, DelegatedKeystore(signingService, certStore))
            return KeyStore.getInstance(name, provider).also { it.load(null) }
        }

        private fun getProvider(): DelegatedKeystoreProvider {
            val provider = Security.getProvider(PROVIDER_NAME)
            return if (provider != null) {
                provider as DelegatedKeystoreProvider
            } else {
                DelegatedKeystoreProvider().apply { Security.addProvider(this) }
            }
        }
    }

    /**
     * Adds a KeyStore type services for providing any class.
     * @param algorithm – the algorithm name
     * @param className – the name of the class implementing this service
     * @param instance - object of the class
     */
    fun putKeyStoreService(algorithm: String, className: String?, instance: Any) {
        putService(object : Service(this, "KeyStore", algorithm, className, null, null) {
            @Throws(NoSuchAlgorithmException::class)
            override fun newInstance(constructorParameter: Any?): Any = instance
        })
    }
}

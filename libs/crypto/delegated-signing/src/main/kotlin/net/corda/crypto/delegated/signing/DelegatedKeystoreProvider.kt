package net.corda.crypto.delegated.signing

import net.corda.crypto.delegated.signing.DelegatedSignerInstaller.Companion.RSA_SIGNING_ALGORITHM
import java.security.Provider
import java.security.Security

internal class DelegatedKeystoreProvider : Provider(
    PROVIDER_NAME,
    "0.2",
    "JCA/JCE delegated keystore provider",
) {

    companion object {
        private const val PROVIDER_NAME = "DelegatedKeyStore"

        val provider: DelegatedKeystoreProvider
            get() =
                Security.getProvider(PROVIDER_NAME) as DelegatedKeystoreProvider? ?: DelegatedKeystoreProvider().also {
                    Security.insertProviderAt(it, 1)
                }
    }
    init {
        this["AlgorithmParameters.EC"] = "sun.security.util.ECParameters"
    }

    fun putServices(name: String, signer: DelegatedSigner, certificatesStores: DelegatedCertificateStore) {
        if (getService("Signature", RSA_SIGNING_ALGORITHM) == null) {
            putService(DelegatedSignatureService(RSA_SIGNING_ALGORITHM, null))
            Hash.values().forEach {
                putService(DelegatedSignatureService(it.ecName, it))
            }
        }

        putService(DelegatedKeyStoreService(name, certificatesStores, signer))
    }

    private inner class DelegatedKeyStoreService(
        name: String,
        private val certificatesStore: DelegatedCertificateStore,
        private val signer: DelegatedSigner,
    ) : Service(
        this@DelegatedKeystoreProvider,
        "KeyStore",
        name,
        "DelegatedKeyStore",
        null,
        null,
    ) {
        override fun newInstance(constructorParameter: Any?): Any {
            return DelegatedKeystore(certificatesStore, signer)
        }
    }
    private inner class DelegatedSignatureService(
        algorithm: String,
        private val defaultHash: Hash?,
    ) : Service(
        this@DelegatedKeystoreProvider,
        "Signature",
        algorithm,
        DelegatedSignature::class.java.name,
        null,
        mapOf("SupportedKeyClasses" to DelegatedPrivateKey::class.java.name)
    ) {
        override fun newInstance(constructorParameter: Any?): Any {
            return DelegatedSignature(defaultHash)
        }
    }
}

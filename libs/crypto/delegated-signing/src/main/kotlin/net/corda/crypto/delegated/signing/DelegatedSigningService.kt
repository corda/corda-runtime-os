package net.corda.crypto.delegated.signing

import java.security.KeyStore
import java.security.Provider
import java.security.Security
import java.security.cert.Certificate
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec

abstract class DelegatedSigningService(
    private val name: String
) {
    companion object {
        const val RSA_SIGNING_ALGORITHM = "RSASSA-PSS"
    }
    init {
        val provider = Security.getProvider(DelegatedKeystoreProvider.PROVIDER_NAME)
        val delegatedKeystoreProvider = if (provider != null) {
            provider as DelegatedKeystoreProvider
        } else {
            DelegatedKeystoreProvider().apply { Security.addProvider(this) }
        }
        delegatedKeystoreProvider.putService(name, this)
    }

    abstract val aliases: Collection<Alias>
    enum class Hash(val hashName: String, private val saltLength: Int) {
        SHA256("SHA-256", 32),
        SHA384("SHA-384", 48),
        SHA512("SHA-512", 64);

        val rsaParameter by lazy {
            PSSParameterSpec(
                hashName,
                "MGF1",
                MGF1ParameterSpec(hashName),
                saltLength,
                1
            )
        }

        val ecName = "${name}withECDSA"
    }

    protected fun findOriginalSignatureProvider(algorithm: String): Provider {
        return Security.getProviders()
            .filter {
                it !is DelegatedKeystoreProvider &&
                    it !is DelegatedSignatureProvider
            }
            .flatMap {
                it.services
            }.filter {
                it.algorithm == algorithm
            }.firstOrNull {
                it.type == "Signature"
            }?.provider ?: throw SecurityException("Could not find a signature provider for $algorithm")
    }

    interface Alias {
        val name: String
        val certificates: Collection<Certificate>

        fun sign(hash: Hash, data: ByteArray): ByteArray
    }

    fun asKeyStore(): KeyStore {
        return KeyStore.getInstance(name).also {
            it.load(null)
        }
    }
}

package net.corda.p2p.gateway.messaging

import net.corda.crypto.delegated.signing.DelegatedSigningService
import java.io.ByteArrayInputStream
import java.security.KeyStoreSpi
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Security
import java.security.Signature
import java.security.cert.Certificate

class JksDelegatedSigningService(
    name: String,
    rawData: ByteArray,
    password: String,
) : DelegatedSigningService(name) {

    private val originalServiceProvider by lazy {
        Security.getProviders()
            .flatMap {
                it.services
            }.filter {
                it.algorithm == "JKS"
            }.firstOrNull {
                it.type == "KeyStore"
            } ?: throw SecurityException("Could not load a JKS key store service")
    }

    private enum class SupportedAlgorithms {
        RSA,
        EC;

        companion object {
            fun fromPublicKey(publicKey: PublicKey): SupportedAlgorithms {
                return values().firstOrNull {
                    it.name == publicKey.algorithm
                } ?: throw SecurityException("Unsupported algorithm ${publicKey.algorithm}")
            }
        }
    }

    private val originalSpi by lazy {
        originalServiceProvider.newInstance(null)
            as? KeyStoreSpi
            ?: throw SecurityException("Could not create key store service")
    }

    override val aliases: Collection<Alias> by lazy {
        ByteArrayInputStream(rawData).use { data ->
            originalSpi.engineLoad(data, password.toCharArray())
        }
        originalSpi.engineAliases().asSequence().map { alias ->
            val privateKey = originalSpi.engineGetKey(alias, password.toCharArray()) as PrivateKey
            val certificates = originalSpi.engineGetCertificateChain(alias).toList()

            certificates.firstOrNull()?.publicKey?.let { publicKey ->
                when (SupportedAlgorithms.fromPublicKey(publicKey)) {
                    SupportedAlgorithms.RSA -> JksRsaAlias(alias, certificates, privateKey)
                    SupportedAlgorithms.EC -> JksEcAlias(alias, certificates, privateKey)
                }
            }
        }.filterNotNull()
            .toList()
    }

    private val ecSignatureProviders by lazy {
        Hash.values().associateWith { hash ->
            findOriginalSignatureProvider(hash.ecName)
        }
    }

    private val rsaSignatureProvider by lazy {
        findOriginalSignatureProvider(RSA_SIGNING_ALGORITHM)
    }

    private inner class JksEcAlias(
        override val name: String,
        override val certificates: Collection<Certificate>,
        private val privateKey: PrivateKey,
    ) : Alias {

        override fun sign(hash: Hash, data: ByteArray): ByteArray {
            val provider = ecSignatureProviders[hash] ?: throw SecurityException("Could not find a signature provider for ${hash.ecName}")
            val signature = Signature.getInstance(
                hash.ecName,
                provider
            )
            signature.initSign(privateKey)
            signature.update(data)
            return signature.sign()
        }
    }

    private inner class JksRsaAlias(
        override val name: String,
        override val certificates: Collection<Certificate>,
        private val privateKey: PrivateKey,
    ) : Alias {

        override fun sign(hash: Hash, data: ByteArray): ByteArray {
            val signature = Signature.getInstance(
                RSA_SIGNING_ALGORITHM,
                rsaSignatureProvider
            )
            val parameter = hash.rsaParameter
            signature.initSign(privateKey)
            signature.setParameter(parameter)
            signature.update(data)
            return signature.sign()
        }
    }
}

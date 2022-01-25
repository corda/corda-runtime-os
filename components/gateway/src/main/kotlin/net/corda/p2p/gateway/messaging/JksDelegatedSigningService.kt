package net.corda.p2p.gateway.messaging

import net.corda.crypto.delegated.signing.DelegatedCertificatesStore
import net.corda.crypto.delegated.signing.DelegatedSigner
import net.corda.crypto.delegated.signing.DelegatedSignerInstaller
import net.corda.crypto.delegated.signing.DelegatedSignerInstaller.Companion.RSA_SIGNING_ALGORITHM
import java.io.ByteArrayInputStream
import java.security.Key
import java.security.KeyStore
import java.security.KeyStoreSpi
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Security
import java.security.Signature
import java.security.cert.Certificate
import java.util.UUID

class JksDelegatedSigningService(
    rawData: ByteArray,
    private val password: String,
    private val name: String = "Gateway-JKS-Signing-Service-${UUID.randomUUID()}",
    private val installer: DelegatedSignerInstaller = DelegatedSignerInstaller(),
) : DelegatedSigner {
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
            fun fromKey(key: Key): SupportedAlgorithms {
                return values().firstOrNull {
                    it.name == key.algorithm
                } ?: throw SecurityException("Unsupported algorithm ${key.algorithm}")
            }
        }
    }

    private val originalSpi by lazy {
        originalServiceProvider.newInstance(null)
            as? KeyStoreSpi
            ?: throw SecurityException("Could not create key store service")
    }

    private val publicKeyToPrivateKey by lazy {
        aliases.values.mapNotNull { (privateKey, certificates) ->
            certificates.firstOrNull()?.let {
                it.publicKey to privateKey
            }
        }
            .toMap()
    }

    override fun sign(
        publicKey: PublicKey,
        hash: DelegatedSigner.Hash,
        data: ByteArray
    ): ByteArray {
        val privateKey = publicKeyToPrivateKey[publicKey] ?: throw SecurityException("Could not find private key")

        return when (SupportedAlgorithms.fromKey(publicKey)) {
            SupportedAlgorithms.RSA -> signRsa(privateKey, hash, data)
            SupportedAlgorithms.EC -> signEc(privateKey, hash, data)
        }
    }
    private val ecSignatureProviders by lazy {
        DelegatedSigner.Hash.values().associateWith { hash ->
            installer.findOriginalSignatureProvider(hash.ecName)
        }
    }

    private val rsaSignatureProvider by lazy {
        installer.findOriginalSignatureProvider(RSA_SIGNING_ALGORITHM)
    }

    private fun signEc(
        privateKey: PrivateKey,
        hash: DelegatedSigner.Hash,
        data: ByteArray
    ): ByteArray {
        val provider = ecSignatureProviders[hash] ?: throw SecurityException("Could not find a signature provider for ${hash.ecName}")
        val signature = Signature.getInstance(
            hash.ecName,
            provider
        )
        signature.initSign(privateKey)
        signature.update(data)
        return signature.sign()
    }
    private fun signRsa(
        privateKey: PrivateKey,
        hash: DelegatedSigner.Hash,
        data: ByteArray
    ): ByteArray {
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

    fun asKeyStore(): KeyStore {
        val certificates = aliases.map { (alias, certificateAndKeys) ->
            object : DelegatedCertificatesStore {
                override val name = alias
                override val certificates = certificateAndKeys.second
            }
        }
        installer.install(name, this, certificates)

        return KeyStore.getInstance(name).also {
            it.load(null)
        }
    }

    private val aliases: Map<String, Pair<PrivateKey, Collection<Certificate>>> by lazy {
        ByteArrayInputStream(rawData).use { data ->
            originalSpi.engineLoad(data, password.toCharArray())
        }
        originalSpi.engineAliases()
            .asSequence()
            .associateWith { alias ->
                originalSpi.engineGetCertificateChain(alias).toList().let { certificates ->
                    val privateKey = originalSpi.engineGetKey(alias, password.toCharArray()) as PrivateKey
                    privateKey to certificates
                }
            }
    }
}

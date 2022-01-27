package net.corda.p2p.gateway.messaging

import net.corda.crypto.delegated.signing.DelegatedCertificateStore
import java.io.ByteArrayInputStream
import java.security.KeyStoreSpi
import java.security.PrivateKey
import java.security.Security

internal class JksKeyStoreReader(
    rawData: ByteArray,
    private val password: String,
) {
    private val originalSpi by lazy {
        Security.getProvider("SUN")
            ?.getService("KeyStore", "JKS")
            ?.newInstance(null)
            as? KeyStoreSpi
            ?: throw SecurityException("Could not create key store service")
    }

    private val aliasNames by lazy {
        ByteArrayInputStream(rawData).use { data ->
            originalSpi.engineLoad(data, password.toCharArray())
        }
        originalSpi.engineAliases().asSequence().toList()
    }

    private val publicKeyToPrivateKey by lazy {
        certificateStore.aliasToCertificates.mapNotNull { (alias, certificates) ->
            certificates.firstOrNull()?.publicKey?.let {
                it to originalSpi.engineGetKey(alias, password.toCharArray()) as PrivateKey
            }
        }.toMap()
    }

    val certificateStore by lazy {
        object : DelegatedCertificateStore {
            override val aliasToCertificates by lazy {
                aliasNames.associateWith {
                    originalSpi.engineGetCertificateChain(it)
                        .toList()
                }
            }
        }
    }

    val signer by lazy {
        JksSigner(publicKeyToPrivateKey)
    }
}

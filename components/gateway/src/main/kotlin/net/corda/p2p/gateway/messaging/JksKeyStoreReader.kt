package net.corda.p2p.gateway.messaging

import net.corda.crypto.delegated.signing.DelegatedCertificateStore
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.PrivateKey

internal class JksKeyStoreReader(
    rawData: ByteArray,
    private val password: String,
) {
    private val originalKeystore by lazy {
        KeyStore.getInstance("JKS").also { keystore ->
            ByteArrayInputStream(rawData).use { data ->
                keystore.load(data, password.toCharArray())
            }
        }
    }

    private val aliasNames by lazy {
        originalKeystore.aliases().toList()
    }

    private val publicKeyToPrivateKey by lazy {
        certificateStore.aliasToCertificates.mapNotNull { (alias, certificates) ->
            certificates.firstOrNull()?.publicKey?.let {
                it to originalKeystore.getKey(alias, password.toCharArray()) as PrivateKey
            }
        }.toMap()
    }

    val certificateStore by lazy {
        object : DelegatedCertificateStore {
            override val aliasToCertificates by lazy {
                aliasNames.associateWith {
                    originalKeystore.getCertificateChain(it)
                        .toList()
                }
            }
        }
    }

    val signer by lazy {
        JksSigner(publicKeyToPrivateKey)
    }
}

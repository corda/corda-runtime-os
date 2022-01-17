package com.r3.corda.utils.provider

import java.math.BigInteger
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.Provider
import java.security.Security
import java.security.interfaces.RSAKey

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

open class DelegatedPrivateKey(
    private val algorithm: String,
    private val format: String,
    private val signOp: (String, ByteArray) -> ByteArray?,
) : PrivateKey {
    companion object {
        fun create(algorithm: String, format: String, signOp: (String, ByteArray) -> ByteArray?): DelegatedPrivateKey {
            return when (algorithm) {
                "RSA" -> DelegatedRSAPrivateKey(algorithm, format, signOp)
                else -> DelegatedPrivateKey(algorithm, format, signOp)
            }
        }
    }

    override fun getFormat() = format
    fun sign(sigAlgo: String, data: ByteArray): ByteArray? = signOp(sigAlgo, data)
    override fun getAlgorithm() = algorithm
    override fun getEncoded(): ByteArray {
        throw UnsupportedOperationException()
    }
}

class DelegatedRSAPrivateKey(algorithm: String, format: String, signOp: (String, ByteArray) -> ByteArray?) :
    DelegatedPrivateKey(algorithm, format, signOp), RSAKey {

    companion object {
        private val dummyKeySize = BigInteger.valueOf(1L).shiftLeft(8191)
    }

    override fun getModulus(): BigInteger {
        return dummyKeySize
    }
}

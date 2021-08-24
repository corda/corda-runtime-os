package net.corda.p2p.gateway.keystore

import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import net.corda.crypto.SigningService
import net.corda.v5.base.util.contextLogger

/**
 * [DelegatedSigningService] implementation using [SigningService].
 */
class DelegatedSigningServiceImpl(private val keystore: KeyStore,
                                  private val keystorePassword: CharArray) : DelegatedSigningService {
    companion object {
        const val RSA_PSS = "RSASSA-PSS"
        private val logger = contextLogger()
    }

    @Suppress("TooGenericExceptionCaught")
    override fun sign(alias: String, data: ByteArray, signAlgorithm: String): ByteArray? {
      logger.info("Signing using delegated key: $alias, algorithm: $signAlgorithm")
        val signature = Signature.getInstance(signAlgorithm, "BC")
        if (signAlgorithm == RSA_PSS) {
            val spec = PSSParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec("SHA-256"),
                32,
                PSSParameterSpec.TRAILER_FIELD_BC)
            signature.setParameter(spec)
        }
        signature.initSign(keystore.getKey(alias, keystorePassword) as PrivateKey)
        signature.update(data)
        return signature.sign()
    }
}

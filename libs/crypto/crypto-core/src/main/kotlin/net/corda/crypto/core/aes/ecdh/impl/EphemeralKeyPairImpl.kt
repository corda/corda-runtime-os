package net.corda.crypto.core.aes.ecdh.impl

import net.corda.crypto.core.Encryptor
import net.corda.crypto.core.aes.AES_KEY_ALGORITHM
import net.corda.crypto.core.aes.AesEncryptor
import net.corda.crypto.core.aes.AesKey
import net.corda.crypto.core.aes.ECDH_KEY_AGREEMENT_ALGORITHM
import net.corda.crypto.core.aes.ecdh.ECDHAgreementParams
import net.corda.crypto.core.aes.ecdh.EphemeralKeyPair
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.KeyScheme
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.jcajce.provider.util.DigestFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import javax.crypto.KeyAgreement
import javax.crypto.spec.SecretKeySpec

class EphemeralKeyPairImpl(
    private val schemeMetadata: CipherSchemeMetadata,
    private val scheme: KeyScheme
) : EphemeralKeyPair {
    private val keyPair: KeyPair

    override val publicKey: PublicKey get() = keyPair.public

    init {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            scheme.algorithmName,
            schemeMetadata.providers.getValue(scheme.providerName)
        )
        if (scheme.algSpec != null) {
            keyPairGenerator.initialize(scheme.algSpec, schemeMetadata.secureRandom)
        } else if (scheme.keySize != null) {
            keyPairGenerator.initialize(scheme.keySize!!, schemeMetadata.secureRandom)
        }
        keyPair = keyPairGenerator.generateKeyPair()
    }

    override fun deriveSharedEncryptor(
        otherEphemeralPublicKey: PublicKey,
        params: ECDHAgreementParams,
        info: ByteArray
    ): Encryptor {
        require(params.salt.isNotEmpty()) {
            "The salt must not be empty"
        }
        require(info.isNotEmpty()) {
            "The info must not be empty"
        }
        require(params.length > 0) {
            "The length must be greater than 0"
        }
        val ikm = KeyAgreement.getInstance(
            ECDH_KEY_AGREEMENT_ALGORITHM,
            schemeMetadata.providers.getValue(scheme.providerName)
        ).apply {
            init(keyPair.private)
            doPhase(otherEphemeralPublicKey, true)
        }.generateSecret()
        val okm = ByteArray(params.length)
        HKDFBytesGenerator(DigestFactory.getDigest(params.digestName)).apply {
            init(HKDFParameters(ikm, params.salt, info))
            generateBytes(okm, 0, params.length)
        }
        return AesEncryptor(AesKey(SecretKeySpec(okm, AES_KEY_ALGORITHM)))
    }
}
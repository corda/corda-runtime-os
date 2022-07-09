package net.corda.crypto.core.aes.ecdh.impl

import net.corda.crypto.core.aes.AES_KEY_ALGORITHM
import net.corda.crypto.core.aes.AES_KEY_SIZE_BYTES
import net.corda.crypto.core.aes.ECDH_KEY_AGREEMENT_ALGORITHM
import net.corda.crypto.core.aes.GCM_NONCE_LENGTH
import net.corda.crypto.core.aes.ecdh.AesGcmEncryptor
import net.corda.crypto.core.aes.ecdh.AgreementParams
import net.corda.crypto.core.aes.ecdh.ECDHKeyPair
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.KeyScheme
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.jcajce.provider.util.DigestFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Provider
import java.security.PublicKey
import javax.crypto.KeyAgreement
import javax.crypto.spec.SecretKeySpec

class EphemeralKeyPairImpl private constructor(
    private val provider: Provider,
    private val keyPair: KeyPair
) : ECDHKeyPair {
    companion object {
        fun create(
            schemeMetadata: CipherSchemeMetadata,
            scheme: KeyScheme
        ): EphemeralKeyPairImpl {
            val provider = schemeMetadata.providers.getValue(scheme.providerName)
            val keyPairGenerator = KeyPairGenerator.getInstance(
                scheme.algorithmName,
                provider
            )
            if (scheme.algSpec != null) {
                keyPairGenerator.initialize(scheme.algSpec, schemeMetadata.secureRandom)
            } else if (scheme.keySize != null) {
                keyPairGenerator.initialize(scheme.keySize!!, schemeMetadata.secureRandom)
            }
            return EphemeralKeyPairImpl(
                provider,
                keyPairGenerator.generateKeyPair()
            )
        }

        fun create(
            schemeMetadata: CipherSchemeMetadata,
            keyPair: KeyPair
        ): EphemeralKeyPairImpl = EphemeralKeyPairImpl(
            schemeMetadata.providers.getValue(schemeMetadata.findKeyScheme(keyPair.public).providerName),
            keyPair
        )
    }

    override val publicKey: PublicKey get() = keyPair.public

    override fun deriveEncryptor(
        otherEphemeralPublicKey: PublicKey,
        params: AgreementParams,
        info: ByteArray
    ): AesGcmEncryptor {
        require(params.salt.isNotEmpty()) {
            "The salt must not be empty"
        }
        require(info.isNotEmpty()) {
            "The info must not be empty"
        }
        val ikm = KeyAgreement.getInstance(ECDH_KEY_AGREEMENT_ALGORITHM, provider).apply {
            init(keyPair.private)
            doPhase(otherEphemeralPublicKey, true)
        }.generateSecret()
        val okm = ByteArray(AES_KEY_SIZE_BYTES + GCM_NONCE_LENGTH)
        HKDFBytesGenerator(DigestFactory.getDigest(params.digestName)).apply {
            init(HKDFParameters(ikm, params.salt, info))
            generateBytes(okm, 0, okm.size)
        }
        return AesGcmEncryptor(SecretKeySpec(okm, AES_KEY_ALGORITHM))
    }
}
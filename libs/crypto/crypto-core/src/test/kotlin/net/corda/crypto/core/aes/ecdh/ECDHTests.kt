package net.corda.crypto.core.aes.ecdh

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.cipher.suite.impl.SignatureVerificationServiceImpl
import net.corda.crypto.core.aes.ecdh.impl.ECDHFactoryImpl
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.SignatureVerificationService
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import org.bouncycastle.jcajce.provider.util.DigestFactory
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import java.security.KeyPair
import java.security.KeyPairGenerator

class ECDHTests {
    private val schemeMetadata: CipherSchemeMetadata = CipherSchemeMetadataImpl()
    private val digestService: DigestService = DigestServiceImpl(schemeMetadata, null)
    private val verifier: SignatureVerificationService = SignatureVerificationServiceImpl(schemeMetadata, digestService)
    private val factory: ECDHFactory = ECDHFactoryImpl(schemeMetadata)
    private val mgmStableKeyPair = generateStableKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME)
    private val params = AgreementParams(
        salt = ByteArray(DigestFactory.getDigest("SHA-256").digestSize).apply {
            schemeMetadata.secureRandom.nextBytes(this)
        },
        digestName = "SHA-256",
        length = 32
    )

    @Test
    fun `Should run through handshake using same shared key to send and receive`() {
        val info = "Hello World!".toByteArray()
        val signatureSpec = SignatureSpec.ECDSA_SHA256

        val member = factory.createInitiator(
            mgmStableKeyPair.public
        )

        val mgm = factory.createReplier(
            mgmStableKeyPair.public
        )

        val initiatingHandshake = member.createInitiatingHandshake(
            params
        )

        val initiatingHandshakeEncryptedBytes = member.encryptor.encrypt(initiatingHandshake.asBytes())
        // send initiatingHandshake: member -> mgm

        val replyHandshake = mgm.produceReplyHandshake(
            initiatingHandshakeEncryptedBytes,
            info
        )

        // send replyHandshake: mgm -> member

        member.processReplyHandshake(replyHandshake, info)

        val plainText = "Hello World!".toByteArray()
        val cipherTextA = member.encryptor.encrypt(plainText)

        // send cipherTextA: member -> mgm

        val plainTextB = mgm.encryptor.decrypt(cipherTextA)

        assertArrayEquals(plainText, plainTextB)
    }

    private fun generateStableKeyPair(schemeMetadata: CipherSchemeMetadata, schemeName: String): KeyPair {
        val scheme = schemeMetadata.findKeyScheme(schemeName)
        val keyPairGenerator = KeyPairGenerator.getInstance(
            scheme.algorithmName,
            schemeMetadata.providers.getValue(scheme.providerName)
        )
        if (scheme.algSpec != null) {
            keyPairGenerator.initialize(scheme.algSpec, schemeMetadata.secureRandom)
        } else if (scheme.keySize != null) {
            keyPairGenerator.initialize(scheme.keySize!!, schemeMetadata.secureRandom)
        }
        return keyPairGenerator.generateKeyPair()
    }
}
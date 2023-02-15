package net.corda.crypto.hes.impl

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.component.test.utils.generateKeyPair
import net.corda.crypto.hes.HybridEncryptionParams
import net.corda.crypto.hes.core.impl.deriveDHSharedSecret
import net.corda.crypto.hes.impl.infra.TestCryptoOpsClient
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.test.impl.TestLifecycleCoordinatorFactoryImpl
import net.corda.test.util.eventually
import net.corda.v5.base.util.EncodingUtils.toHex
import net.corda.v5.crypto.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.SM2_CODE_NAME
import net.corda.v5.crypto.X25519_CODE_NAME
import net.corda.v5.crypto.sha256Bytes
import org.bouncycastle.jcajce.provider.util.DigestFactory
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import java.security.KeyPair
import java.security.PublicKey
import java.util.UUID
import kotlin.test.assertEquals

class HybridEncryptionSchemeTests {
    companion object {
        private lateinit var tenantId: String
        private lateinit var coordinatorFactory: TestLifecycleCoordinatorFactoryImpl
        private lateinit var schemeMetadata: CipherSchemeMetadata
        private lateinit var cryptoOpsClient: TestCryptoOpsClient
        private lateinit var mgmStableKeyPairs: Map<PublicKey, KeyPair>
        private lateinit var ecdhKeySchemes: List<KeyScheme>
        private lateinit var ephemeralEncryptor: EphemeralKeyPairEncryptorImpl
        private lateinit var stableDecryptor: StableKeyPairDecryptorImpl

        @BeforeAll
        @JvmStatic
        fun setup() {
            tenantId = toHex(UUID.randomUUID().toString().toByteArray().sha256Bytes()).take(12)
            coordinatorFactory = TestLifecycleCoordinatorFactoryImpl()
            schemeMetadata = CipherSchemeMetadataImpl()
            ecdhKeySchemes = listOf(
                schemeMetadata.findKeyScheme(X25519_CODE_NAME),
                schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME),
                schemeMetadata.findKeyScheme(ECDSA_SECP256K1_CODE_NAME),
                schemeMetadata.findKeyScheme(SM2_CODE_NAME)
            )
            mgmStableKeyPairs = ecdhKeySchemes.map {
                generateKeyPair(schemeMetadata, it.codeName)
            }.associateBy { it.public }
            cryptoOpsClient = TestCryptoOpsClient(
                coordinatorFactory,
                mock {
                    on { deriveSharedSecret(any(), any(), any(), any()) } doAnswer {
                        val pair = mgmStableKeyPairs.getValue(it.getArgument(1))
                        val otherPublicKey: PublicKey = it.getArgument(2)
                        val provider =
                            schemeMetadata.providers.getValue(schemeMetadata.findKeyScheme(otherPublicKey).providerName)
                        deriveDHSharedSecret(provider, pair.private, otherPublicKey)
                    }
                }
            ).also { it.start() }
            ephemeralEncryptor = EphemeralKeyPairEncryptorImpl(schemeMetadata)
            stableDecryptor = StableKeyPairDecryptorImpl(coordinatorFactory, schemeMetadata, cryptoOpsClient).also {
                it.start()
            }
            eventually {
                assertEquals(LifecycleStatus.UP, stableDecryptor.lifecycleCoordinator.status)
            }
        }

        @JvmStatic
        fun stablePublicKeys(): List<PublicKey> = mgmStableKeyPairs.map { it.key }
    }

    private fun generateSalt() = ByteArray(DigestFactory.getDigest("SHA-256").digestSize).apply {
        schemeMetadata.secureRandom.nextBytes(this)
    }

    @ParameterizedTest
    @MethodSource("stablePublicKeys")
    @Suppress("MaxLineLength")
    fun `Should run through handshake using same shared key to send and receive without aad for all supported key schemes`(
        stablePublicKey: PublicKey
    ) {
        val plainText = "Hello MGM!".toByteArray()
        val cipherText = ephemeralEncryptor.encrypt(
            otherPublicKey = stablePublicKey,
            plainText = plainText,
        ) { ek, sk -> HybridEncryptionParams( generateSalt() + ek.encoded + sk.encoded, null) }
        val decryptedPlainTex = stableDecryptor.decrypt(
            tenantId = tenantId,
            salt = cipherText.params.salt,
            publicKey = stablePublicKey,
            otherPublicKey = cipherText.publicKey,
            cipherText = cipherText.cipherText,
            aad = null
        )
        assertArrayEquals(plainText, decryptedPlainTex)
    }

    @ParameterizedTest
    @MethodSource("stablePublicKeys")
    @Suppress("MaxLineLength")
    fun `Should run through handshake using same shared key to send and receive with aad for all supported key schemes`(
        stablePublicKey: PublicKey
    ) {
        val plainText = "Hello MGM!".toByteArray()
        val cipherText = ephemeralEncryptor.encrypt(
            otherPublicKey = stablePublicKey,
            plainText = plainText
        ) { ek, sk -> HybridEncryptionParams( generateSalt() + ek.encoded + sk.encoded, "Something New".toByteArray()) }
        val decryptedPlainTex = stableDecryptor.decrypt(
            tenantId = tenantId,
            salt = cipherText.params.salt,
            publicKey = stablePublicKey,
            otherPublicKey = cipherText.publicKey,
            cipherText = cipherText.cipherText,
            aad =  cipherText.params.aad
        )
        assertArrayEquals(plainText, decryptedPlainTex)
    }
}
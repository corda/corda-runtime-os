package net.corda.crypto.ecdh.impl

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.component.test.utils.generateKeyPair
import net.corda.crypto.ecdh.impl.infra.TestCryptoOpsClient
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.test.impl.TestLifecycleCoordinatorFactoryImpl
import net.corda.test.util.eventually
import net.corda.v5.base.util.toHex
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.SM2_CODE_NAME
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

class ProtocolTests {
    companion object {
        private val digestName = "SHA-256"
        private lateinit var tenantId: String
        private lateinit var coordinatorFactory: TestLifecycleCoordinatorFactoryImpl
        private lateinit var schemeMetadata: CipherSchemeMetadata
        private lateinit var cryptoOpsClient: TestCryptoOpsClient
        private lateinit var ephemeralProvider: EphemeralKeyPairProviderImpl
        private lateinit var stableProvider: StableKeyPairProviderImpl
        private lateinit var mgmStableKeyPairs: Map<PublicKey, KeyPair>
        private lateinit var ecdhKeySchemes: List<KeyScheme>

        @BeforeAll
        @JvmStatic
        fun setup() {
            tenantId = UUID.randomUUID().toString().toByteArray().sha256Bytes().toHex().take(12)
            coordinatorFactory = TestLifecycleCoordinatorFactoryImpl()
            schemeMetadata = CipherSchemeMetadataImpl()
            ecdhKeySchemes = listOf(
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
                        val provider = schemeMetadata.providers.getValue(schemeMetadata.findKeyScheme(otherPublicKey).providerName)
                        EphemeralKeyPair.deriveSharedSecret(provider, pair.private, otherPublicKey)
                    }
                }
            ).also { it.start() }
            ephemeralProvider = EphemeralKeyPairProviderImpl(schemeMetadata)
            stableProvider = StableKeyPairProviderImpl(coordinatorFactory, cryptoOpsClient).also { it.start() }
            eventually {
                assertEquals(LifecycleStatus.UP, stableProvider.lifecycleCoordinator.status)
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
        val salt = generateSalt()
        val info = "Hello".toByteArray()

        val member = ephemeralProvider.create(stablePublicKey, digestName)
        val mgm = stableProvider.create(tenantId, stablePublicKey, member.publicKey, digestName)

        val plainTextA = "Hello MGM!".toByteArray()
        val cipherTextA = member.encrypt(salt, info, plainTextA)
        val decryptedPlainTexA = mgm.decrypt(salt, info, cipherTextA)
        assertArrayEquals(plainTextA, decryptedPlainTexA)

        val plainTextB = "Hello member!".toByteArray()
        val cipherTextB = mgm.encrypt(salt, info, plainTextB)
        val decryptedPlainTexB = member.decrypt(salt, info, cipherTextB)
        assertArrayEquals(plainTextB, decryptedPlainTexB)
    }

    @ParameterizedTest
    @MethodSource("stablePublicKeys")
    @Suppress("MaxLineLength")
    fun `Should run through handshake using same shared key to send and receive with aad for all supported key schemes`(
        stablePublicKey: PublicKey
    ) {
        val salt = generateSalt()
        val info = "Hello".toByteArray()
        val aad = "Something New".toByteArray()

        val member = ephemeralProvider.create(stablePublicKey, digestName)
        val mgm = stableProvider.create(tenantId, stablePublicKey, member.publicKey, digestName)

        val plainTextA = "Hello MGM!".toByteArray()
        val cipherTextA = member.encrypt(salt, info, plainTextA, aad)
        val decryptedPlainTexA = mgm.decrypt(salt, info, cipherTextA, aad)
        assertArrayEquals(plainTextA, decryptedPlainTexA)

        val plainTextB = "Hello member!".toByteArray()
        val cipherTextB = mgm.encrypt(salt, info, plainTextB, aad)
        val decryptedPlainTexB = member.decrypt(salt, info, cipherTextB, aad)
        assertArrayEquals(plainTextB, decryptedPlainTexB)
    }

    @ParameterizedTest
    @MethodSource("stablePublicKeys")
    @Suppress("MaxLineLength")
    fun `Should run through handshake using different shared keys in each direction to send and receive with aad for all supported key schemes`(
        stablePublicKey: PublicKey
    ) {
        val salt = generateSalt()
        val info1 = "Hello Service".toByteArray()
        val info2 = "Hello Client".toByteArray()
        val aad = "Something New".toByteArray()

        val member = ephemeralProvider.create(stablePublicKey, digestName)
        val mgm = stableProvider.create(tenantId, stablePublicKey, member.publicKey, digestName)

        val plainTextA = "Hello MGM!".toByteArray()
        val cipherTextA = member.encrypt(salt, info1, plainTextA, aad)
        val decryptedPlainTexA = mgm.decrypt(salt, info1, cipherTextA, aad)
        assertArrayEquals(plainTextA, decryptedPlainTexA)

        val plainTextB = "Hello member!".toByteArray()
        val cipherTextB = mgm.encrypt(salt, info2, plainTextB, aad)
        val decryptedPlainTexB = member.decrypt(salt, info2, cipherTextB, aad)
        assertArrayEquals(plainTextB, decryptedPlainTexB)
    }
}